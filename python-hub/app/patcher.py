from __future__ import annotations

import asyncio
import io
import re
import struct
import uuid
import zipfile
import zlib
from pathlib import Path
from typing import Any
from urllib.parse import quote

import httpx

from .catalog import clean_game_title
from .ia_client import ia_auth_headers

_ROM_INNER_EXTS = {
    ".sfc", ".smc", ".nes", ".gba", ".gb", ".gbc", ".md", ".gen", ".z64",
    ".n64", ".v64", ".sms", ".gg", ".pce", ".bin", ".cue", ".iso", ".nds", ".3ds",
}

_PLATFORM_KEYWORDS: dict[str, str] = {
    "snes": "SNES",
    "nes": "NES",
    "gba": "GBA",
    "gb": '"Game Boy"',
    "gbc": '"Game Boy Color"',
    "genesis": "Genesis",
    "n64": "N64",
    "ps1": "PSX",
    "nds": "NDS",
    "psp": "PSP",
}


def apply_ips(rom_bytes: bytes, patch_bytes: bytes) -> bytes:
    """Apply an IPS binary patch in pure Python without altering input bytes."""
    if len(patch_bytes) < 8 or patch_bytes[:5] != b"PATCH":
        raise ValueError("El archivo no tiene una cabecera IPS válida ('PATCH').")
    out = bytearray(rom_bytes)
    pos = 5
    limit = len(patch_bytes)

    while pos + 3 <= limit:
        marker = patch_bytes[pos : pos + 3]
        if marker == b"EOF":
            pos += 3
            if pos + 3 == limit:
                trunc_size = int.from_bytes(patch_bytes[pos : pos + 3], "big")
                if trunc_size < len(out):
                    del out[trunc_size:]
            return bytes(out)

        offset = int.from_bytes(marker, "big")
        pos += 3
        if pos + 2 > limit:
            raise ValueError("Parche IPS truncado leyendo el tamaño de bloque.")
        size = int.from_bytes(patch_bytes[pos : pos + 2], "big")
        pos += 2

        if size == 0:
            if pos + 3 > limit:
                raise ValueError("Parche IPS truncado leyendo bloque RLE.")
            rle_len = int.from_bytes(patch_bytes[pos : pos + 2], "big")
            val = patch_bytes[pos + 2]
            pos += 3
            end = offset + rle_len
            if end > len(out):
                out.extend(b"\x00" * (end - len(out)))
            out[offset:end] = bytes([val]) * rle_len
        else:
            if pos + size > limit:
                raise ValueError("Parche IPS truncado leyendo payload.")
            end = offset + size
            if end > len(out):
                out.extend(b"\x00" * (end - len(out)))
            out[offset:end] = patch_bytes[pos : pos + size]
            pos += size

    raise ValueError("Parche IPS inválido: falta el marcador 'EOF'.")


def _read_bps_vlq(buf: bytes, pos: int) -> tuple[int, int]:
    data = 0
    shift = 1
    while True:
        if pos >= len(buf):
            raise ValueError("Parche BPS truncado.")
        x = buf[pos]
        pos += 1
        data += (x & 0x7F) * shift
        if x & 0x80:
            return data, pos
        shift <<= 7
        data += shift


def _align_rom_for_bps(rom_bytes: bytes, expected_crc: int, console_key: str) -> tuple[bytes, int]:
    actual_crc = zlib.crc32(rom_bytes) & 0xFFFFFFFF
    if actual_crc == expected_crc:
        return rom_bytes, 0
    if console_key == "snes":
        if len(rom_bytes) > 512 and len(rom_bytes) % 1024 == 512:
            stripped = rom_bytes[512:]
            if (zlib.crc32(stripped) & 0xFFFFFFFF) == expected_crc:
                return stripped, -512
        if len(rom_bytes) % 1024 == 0:
            padded = (b"\x00" * 512) + rom_bytes
            if (zlib.crc32(padded) & 0xFFFFFFFF) == expected_crc:
                return padded, 512
    raise ValueError(
        f"CRC32 incompatible: el parche BPS espera {expected_crc:08X} pero tu ROM tiene {actual_crc:08X}. "
        "Prueba otra revisión regional en MiNERVA."
    )


def apply_bps(rom_bytes: bytes, patch_bytes: bytes, console_key: str = "") -> tuple[bytes, dict[str, Any]]:
    """Apply a BPS1 patch with full CRC32 verification and SNES SMC header alignment."""
    if len(patch_bytes) < 20 or patch_bytes[:4] != b"BPS1":
        raise ValueError("El archivo no tiene una cabecera BPS válida ('BPS1').")

    source_crc, target_crc, patch_crc = struct.unpack("<III", patch_bytes[-12:])
    actual_patch_crc = zlib.crc32(patch_bytes[:-4]) & 0xFFFFFFFF
    if actual_patch_crc != patch_crc:
        raise ValueError("El archivo .bps está corrupto (CRC32 del parche inválido).")

    src, header_shift = _align_rom_for_bps(rom_bytes, source_crc, console_key)
    pos = 4
    body_end = len(patch_bytes) - 12

    source_size, pos = _read_bps_vlq(patch_bytes, pos)
    target_size, pos = _read_bps_vlq(patch_bytes, pos)
    meta_size, pos = _read_bps_vlq(patch_bytes, pos)
    pos += meta_size

    out = bytearray()
    out_offset = 0
    src_rel = 0
    tgt_rel = 0

    while pos < body_end:
        word, pos = _read_bps_vlq(patch_bytes, pos)
        mode = word & 3
        length = (word >> 2) + 1
        if mode == 0:  # SourceRead
            chunk = src[out_offset : out_offset + length]
            if len(chunk) < length:
                chunk += b"\x00" * (length - len(chunk))
            out.extend(chunk)
            out_offset += length
        elif mode == 1:  # TargetRead
            out.extend(patch_bytes[pos : pos + length])
            pos += length
            out_offset += length
        elif mode == 2:  # SourceCopy
            delta, pos = _read_bps_vlq(patch_bytes, pos)
            src_rel += (-1 if (delta & 1) else 1) * (delta >> 1)
            chunk = src[src_rel : src_rel + length]
            if len(chunk) < length:
                chunk += b"\x00" * (length - len(chunk))
            out.extend(chunk)
            src_rel += length
            out_offset += length
        else:  # TargetCopy
            delta, pos = _read_bps_vlq(patch_bytes, pos)
            tgt_rel += (-1 if (delta & 1) else 1) * (delta >> 1)
            for _ in range(length):
                out.append(out[tgt_rel])
                tgt_rel += 1
                out_offset += 1

    if len(out) != target_size:
        out = out[:target_size]
    final_bytes = bytes(out)
    out_crc = zlib.crc32(final_bytes) & 0xFFFFFFFF
    if out_crc != target_crc:
        raise ValueError(f"La ROM resultante no coincide con el CRC32 esperado ({out_crc:08X} != {target_crc:08X}).")

    return final_bytes, {
        "sourceCrc": f"{source_crc:08X}",
        "targetCrc": f"{target_crc:08X}",
        "sourceSize": source_size,
        "targetSize": target_size,
        "headerShift": header_shift,
    }


def extract_patch_payload(raw_bytes: bytes, filename: str = "") -> tuple[bytes, str, str]:
    name_lower = (filename or "").lower()
    if name_lower.endswith(".zip") or raw_bytes[:2] == b"PK":
        try:
            with zipfile.ZipFile(io.BytesIO(raw_bytes)) as zf:
                candidates = [
                    info for info in zf.infolist()
                    if not info.is_dir() and Path(info.filename).suffix.lower() in (".bps", ".ips")
                ]
                if not candidates:
                    raise ValueError("El archivo ZIP no contiene ningún parche .bps o .ips compatible.")
                candidates.sort(
                    key=lambda i: (
                        any(tag in i.filename.lower() for tag in ("esp", "spa", "spanish", "espanol", "castellano", "[es]")),
                        Path(i.filename).suffix.lower() == ".bps",
                    ),
                    reverse=True,
                )
                picked = candidates[0]
                payload = zf.read(picked.filename)
                fmt = Path(picked.filename).suffix.lstrip(".").upper()
                return payload, fmt, Path(picked.filename).name
        except zipfile.BadZipFile as exc:
            raise ValueError("El archivo ZIP de parche está dañado.") from exc

    if raw_bytes[:4] == b"BPS1" or name_lower.endswith(".bps"):
        return raw_bytes, "BPS", Path(filename or "patch.bps").name
    if raw_bytes[:5] == b"PATCH" or name_lower.endswith(".ips"):
        return raw_bytes, "IPS", Path(filename or "patch.ips").name
    raise ValueError("Formato de parche no soportado. Usa archivos .ips, .bps o .zip con uno dentro.")


def read_rom_payload(rom_path: Path) -> tuple[bytes, str]:
    if rom_path.suffix.lower() == ".zip":
        with zipfile.ZipFile(rom_path) as zf:
            members = [i for i in zf.infolist() if not i.is_dir()]
            rom_members = [i for i in members if Path(i.filename).suffix.lower() in _ROM_INNER_EXTS]
            chosen = max(rom_members or members, key=lambda i: i.file_size)
            ext = Path(chosen.filename).suffix.lstrip(".") or "bin"
            return zf.read(chosen.filename), ext
    return rom_path.read_bytes(), rom_path.suffix.lstrip(".") or "bin"


def apply_patch_to_rom_file(
    rom_path: Path,
    console_key: str,
    patch_raw: bytes,
    patch_filename: str,
    output_dir: Path,
    stem_label: str = "",
) -> dict[str, Any]:
    if not rom_path.is_file():
        raise FileNotFoundError(f"ROM origen no encontrada: {rom_path}")

    patch_bytes, fmt, inner_patch_name = extract_patch_payload(patch_raw, patch_filename)
    rom_bytes, out_ext = read_rom_payload(rom_path)

    if fmt == "BPS":
        patched_bytes, meta = apply_bps(rom_bytes, patch_bytes, console_key=console_key)
    else:
        if console_key == "snes" and len(rom_bytes) % 1024 == 512:
            # Strip SMC header when ROM includes 512-byte copier header? Keep standard ROM order, try as-is.
            pass
        patched_bytes = apply_ips(rom_bytes, patch_bytes)
        meta = {}

    safe_stem = re.sub(r'[<>:"/\\|?*]+', "", (stem_label or rom_path.stem)).strip() or rom_path.stem
    if "[es]" not in safe_stem.lower():
        out_name = f"{safe_stem} [ES].{out_ext}"
    else:
        out_name = f"{safe_stem}.{out_ext}"

    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / out_name
    out_path.write_bytes(patched_bytes)
    crc_hex = f"{zlib.crc32(patched_bytes) & 0xFFFFFFFF:08X}"

    return {
        "patchId": uuid.uuid4().hex[:12],
        "format": fmt,
        "innerPatchName": inner_patch_name,
        "patchedFilename": out_name,
        "patchedPath": str(out_path),
        "crc32": crc_hex,
        "bytes": len(patched_bytes),
        **meta,
    }


async def search_spanish_patches(title: str, console_key: str, settings: dict[str, Any]) -> list[dict[str, Any]]:
    clean = clean_game_title(title) or title
    safe_q = re.sub(r'["():]+', " ", clean).strip()
    if len(safe_q) < 2:
        return []

    plat_kw = _PLATFORM_KEYWORDS.get((console_key or "").lower(), "")
    plat_clause = f" AND {plat_kw}" if plat_kw else ""
    ia_query = (
        f'({safe_q}) AND (spanish OR espanol OR traduccion OR castellano OR ips OR bps OR romhacking)'
        f"{plat_clause}"
    )
    headers = ia_auth_headers(settings)
    try:
        async with httpx.AsyncClient(timeout=7.5, follow_redirects=True) as client:
            resp = await client.get(
                "https://archive.org/advancedsearch.php",
                params={
                    "q": ia_query,
                    "fl[]": ["identifier", "title", "item_size"],
                    "rows": 8,
                    "page": 1,
                    "output": "json",
                },
                headers=headers,
            )
            if resp.status_code != 200:
                return []
            docs = resp.json().get("response", {}).get("docs", [])[:5]
            if not docs:
                return []

            async def _inspect_item(doc: dict[str, Any]) -> list[dict[str, Any]]:
                ident = str(doc.get("identifier") or "").strip()
                item_title = str(doc.get("title") or ident)
                if not ident:
                    return []
                try:
                    fr = await client.get(f"https://archive.org/metadata/{quote(ident)}/files", headers=headers)
                    if fr.status_code != 200:
                        return []
                    raw_files = fr.json().get("result") or []
                    out: list[dict[str, Any]] = []
                    for f in raw_files:
                        name = str(f.get("name") or "")
                        low = name.lower()
                        ext = Path(name).suffix.lower()
                        if ext not in (".ips", ".bps", ".zip"):
                            continue
                        if ext == ".zip" and not any(tok in low for tok in ("patch", "esp", "spa", "tradu", "es]", "ips", "bps", safe_q.lower().split()[0])):
                            continue
                        fmt = ext.lstrip(".").upper()
                        out.append(
                            {
                                "identifier": ident,
                                "title": f"{item_title} — {Path(name).name}",
                                "filename": Path(name).name,
                                "format": fmt,
                                "language": "ES",
                                "size": int(f.get("size") or 0),
                                "patchUrl": f"https://archive.org/download/{quote(ident)}/{quote(name)}",
                                "sourceName": "Internet Archive",
                            }
                        )
                        if len(out) >= 4:
                            break
                    return out
                except Exception:
                    return []

            nested = await asyncio.gather(*[_inspect_item(d) for d in docs])
            merged: list[dict[str, Any]] = []
            for chunk in nested:
                merged.extend(chunk)
            merged.sort(key=lambda r: (r["format"] in ("BPS", "IPS"), "esp" in r["filename"].lower() or "es" in r["filename"].lower()), reverse=True)
            return merged[:12]
    except Exception:
        return []
