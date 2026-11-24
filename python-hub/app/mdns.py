from __future__ import annotations

import re
import socket
import struct
import threading
import time
from typing import Any

MDNS_ADDR = "224.0.0.251"
MDNS_PORT = 5353
SERVICE_TYPE = "_ozvault._tcp.local."
DNS_SD_META = "_services._dns-sd._udp.local."

_TYPE_A = 1
_TYPE_PTR = 12
_TYPE_TXT = 16
_TYPE_SRV = 33
_CLASS_IN = 1
_CLASS_IN_CACHE_FLUSH = 0x8001


def _sanitize_label(raw: str, fallback: str = "ozvault") -> str:
    cleaned = re.sub(r"[^A-Za-z0-9-]+", "-", (raw or "").strip()).strip("-")
    return (cleaned[:48] or fallback)


def encode_dns_name(name: str) -> bytes:
    clean = (name or "").strip().rstrip(".")
    if not clean:
        return b"\x00"
    out = bytearray()
    for part in clean.split("."):
        encoded = part.encode("utf-8")[:63]
        out.append(len(encoded))
        out.extend(encoded)
    out.append(0)
    return bytes(out)


def encode_txt_records(items: dict[str, str]) -> bytes:
    if not items:
        return b"\x00"
    out = bytearray()
    for k, v in items.items():
        entry = f"{k}={v}".encode("utf-8")[:255]
        out.append(len(entry))
        out.extend(entry)
    return bytes(out)


def _pack_rr(name: str, rtype: int, rclass: int, ttl: int, rdata: bytes) -> bytes:
    return encode_dns_name(name) + struct.pack("!HHIH", rtype, rclass, max(0, int(ttl)), len(rdata)) + rdata


def build_announcement_packet(
    instance_name: str,
    hostname_local: str,
    ipv4: str,
    port: int,
    txt: dict[str, str],
    ttl: int = 120,
) -> bytes:
    """Build a standard RFC-6762 mDNS DNS-SD response packet for _ozvault._tcp.local."""
    safe_instance = f"{_sanitize_label(instance_name, 'OzGameVault')}.{SERVICE_TYPE}"
    host_fqdn = hostname_local if hostname_local.endswith(".local.") else f"{_sanitize_label(hostname_local)}.local."

    answers = [
        _pack_rr(DNS_SD_META, _TYPE_PTR, _CLASS_IN, ttl, encode_dns_name(SERVICE_TYPE)),
        _pack_rr(SERVICE_TYPE, _TYPE_PTR, _CLASS_IN, ttl, encode_dns_name(safe_instance)),
    ]
    srv_rdata = struct.pack("!HHH", 0, 0, int(port)) + encode_dns_name(host_fqdn)
    additionals = [
        _pack_rr(safe_instance, _TYPE_SRV, _CLASS_IN_CACHE_FLUSH, ttl, srv_rdata),
        _pack_rr(safe_instance, _TYPE_TXT, _CLASS_IN_CACHE_FLUSH, ttl, encode_txt_records(txt)),
        _pack_rr(host_fqdn, _TYPE_A, _CLASS_IN_CACHE_FLUSH, ttl, socket.inet_aton(ipv4)),
    ]

    header = struct.pack("!HHHHHH", 0, 0x8400, 0, len(answers), 0, len(additionals))
    return header + b"".join(answers) + b"".join(additionals)


def build_goodbye_packet(
    instance_name: str,
    hostname_local: str,
    ipv4: str,
    port: int,
    txt: dict[str, str],
) -> bytes:
    return build_announcement_packet(instance_name, hostname_local, ipv4, port, txt, ttl=0)


def _read_dns_name(packet: bytes, offset: int) -> tuple[str, int]:
    labels: list[str] = []
    jumped = False
    end_offset = offset
    seen_offsets: set[int] = set()

    while offset < len(packet):
        if offset in seen_offsets:
            break
        seen_offsets.add(offset)
        length = packet[offset]
        if length == 0:
            if not jumped:
                end_offset = offset + 1
            break
        if (length & 0xC0) == 0xC0:
            if offset + 1 >= len(packet):
                break
            ptr = ((length & 0x3F) << 8) | packet[offset + 1]
            if not jumped:
                end_offset = offset + 2
            offset = ptr
            jumped = True
            continue
        offset += 1
        if offset + length > len(packet):
            break
        labels.append(packet[offset : offset + length].decode("utf-8", errors="ignore"))
        offset += length
        if not jumped:
            end_offset = offset

    return (".".join(labels) + ".").lower() if labels else "", end_offset


def parse_dns_questions(packet: bytes) -> list[str]:
    """Extract lowercase DNS question names (trailing dot) from an mDNS query packet."""
    if len(packet) < 12:
        return []
    _txid, flags, qdcount, _ancount, _nscount, _arcount = struct.unpack("!HHHHHH", packet[:12])
    if (flags & 0x8000) != 0:
        return []
    offset = 12
    names: list[str] = []
    for _ in range(min(qdcount, 32)):
        qname, offset = _read_dns_name(packet, offset)
        if offset + 4 > len(packet):
            break
        offset += 4  # qtype + qclass
        if qname:
            names.append(qname)
    return names


class MdnsAdvertiser:
    """Best-effort pure-stdlib RFC-6762 mDNS advertiser/responder for LAN discovery."""

    def __init__(
        self,
        port: int,
        lan_ip: str,
        instance_name: str | None = None,
        hostname: str | None = None,
        txt: dict[str, str] | None = None,
    ) -> None:
        raw_host = (hostname or socket.gethostname().split(".")[0] or "ozvault")
        self.port = int(port)
        self.lan_ip = lan_ip if lan_ip and not lan_ip.startswith("127.") else "127.0.0.1"
        self.hostname_label = _sanitize_label(raw_host, "ozvault")
        self.hostname_local = f"{self.hostname_label}.local."
        self.instance_name = _sanitize_label(instance_name or f"OzGameVault-{self.hostname_label}", "OzGameVault")
        self.txt = dict(
            txt
            or {
                "version": "2",
                "app": "game-vault",
                "events": "/api/v1/events",
                "sync": "/api/v1/sync",
                "port": str(self.port),
            }
        )
        self._sock: socket.socket | None = None
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None
        self._running = False
        self._last_error = ""

    def start(self) -> bool:
        if self._running:
            return True
        if self.lan_ip == "127.0.0.1":
            self._last_error = "Sin IP LAN activa"
            return False
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            if hasattr(socket, "SO_REUSEPORT"):
                try:
                    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                except OSError:
                    pass
            sock.bind(("", MDNS_PORT))
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 1)
            try:
                sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(self.lan_ip))
            except OSError:
                pass
            mreq = struct.pack("4s4s", socket.inet_aton(MDNS_ADDR), socket.inet_aton("0.0.0.0"))
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
            sock.settimeout(0.8)
            self._sock = sock
            self._stop_event.clear()
            self._running = True
            self._last_error = ""
            self._thread = threading.Thread(target=self._serve_loop, name="oz-mdns", daemon=True)
            self._thread.start()
            return True
        except OSError as exc:
            self._last_error = str(exc)
            self._running = False
            if self._sock is not None:
                try:
                    self._sock.close()
                except OSError:
                    pass
                self._sock = None
            return False

    def _announcement_bytes(self, ttl: int = 120) -> bytes:
        return build_announcement_packet(
            self.instance_name,
            self.hostname_local,
            self.lan_ip,
            self.port,
            self.txt,
            ttl=ttl,
        )

    def _serve_loop(self) -> None:
        sock = self._sock
        if sock is None:
            return
        pkt = self._announcement_bytes(ttl=120)
        targets = {SERVICE_TYPE.lower(), DNS_SD_META.lower(), f"{self.instance_name}.{SERVICE_TYPE}".lower(), self.hostname_local.lower()}
        # Initial bursts + periodic re-announcement every ~45s
        for _ in range(2):
            if self._stop_event.is_set():
                break
            try:
                sock.sendto(pkt, (MDNS_ADDR, MDNS_PORT))
            except OSError:
                break
            time.sleep(0.25)

        last_announce = time.monotonic()
        while not self._stop_event.is_set():
            try:
                data, _addr = sock.recvfrom(4096)
                questions = parse_dns_questions(data)
                if any(q in targets for q in questions):
                    sock.sendto(pkt, (MDNS_ADDR, MDNS_PORT))
            except socket.timeout:
                pass
            except OSError:
                break

            now = time.monotonic()
            if now - last_announce >= 45.0 and not self._stop_event.is_set():
                try:
                    sock.sendto(pkt, (MDNS_ADDR, MDNS_PORT))
                    last_announce = now
                except OSError:
                    break

    def stop(self) -> None:
        self._stop_event.set()
        if self._sock is not None:
            try:
                goodbye = self._announcement_bytes(ttl=0)
                self._sock.sendto(goodbye, (MDNS_ADDR, MDNS_PORT))
            except OSError:
                pass
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=1.2)
        self._thread = None
        self._running = False

    def status(self) -> dict[str, Any]:
        return {
            "enabled": True,
            "running": self._running,
            "serviceType": SERVICE_TYPE,
            "instanceName": self.instance_name,
            "hostname": self.hostname_local.rstrip("."),
            "host": self.lan_ip,
            "port": self.port,
            "error": self._last_error,
        }
