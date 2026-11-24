from __future__ import annotations

import ftplib
import threading
import uuid
from pathlib import Path
from typing import Any

from .catalog import RomRecord


def build_remote_dir(
    target: dict[str, Any],
    console_key: str,
    raw_platform: str = "",
    remote_dir_override: str = "",
) -> str:
    explicit = (remote_dir_override or "").strip().rstrip("/")
    if explicit:
        return explicit
    overrides = {str(k).strip().lower(): str(v).strip() for k, v in (target.get("path_overrides") or {}).items() if str(v).strip()}
    raw_key = (raw_platform or "").strip().lower()
    if raw_key and raw_key in overrides:
        return overrides[raw_key].rstrip("/")
    ck = (console_key or "").strip().lower()
    if ck and ck in overrides:
        return overrides[ck].rstrip("/")
    base = str(target.get("base_path") or "Roms").rstrip("/")
    return f"{base}/{console_key}" if base else console_key


def _ftp_makedirs(ftp: ftplib.FTP, remote_dir: str) -> str:
    """Resolve and ensure remote directory exists, handling read-only '/' FTP roots (e.g. PS4 GoldHEN)."""
    normalized = (remote_dir or "").replace("\\", "/").strip().rstrip("/")
    if not normalized:
        try:
            return ftp.pwd()
        except ftplib.all_errors:
            return "/"

    try:
        ftp.cwd(normalized)
        return normalized
    except ftplib.all_errors:
        pass

    parts = [p for p in normalized.split("/") if p]
    if not normalized.startswith("/") and parts:
        for prefix in ("/data/EmuSuNi", "/mnt/usb0", "/data", "/dev_hdd0"):
            full_cand = f"{prefix}/{normalized}"
            try:
                ftp.cwd(full_cand)
                return full_cand
            except ftplib.all_errors:
                pass
            top_cand = f"{prefix}/{parts[0]}"
            try:
                ftp.cwd(top_cand)
                normalized = full_cand
                break
            except ftplib.all_errors:
                continue

    if normalized.startswith("/"):
        try:
            ftp.cwd("/")
        except ftplib.all_errors:
            pass

    for part in [p for p in normalized.split("/") if p]:
        try:
            ftp.cwd(part)
        except ftplib.all_errors:
            try:
                ftp.mkd(part)
                ftp.cwd(part)
            except ftplib.error_perm as exc:
                cur_pwd = ""
                try:
                    cur_pwd = ftp.pwd()
                except Exception:
                    pass
                if "550" in str(exc):
                    if cur_pwd == "/":
                        raise RuntimeError(
                            "Error FTP 550: la raíz '/' es de solo lectura en la consola. "
                            "Usa una ruta absoluta como /data/EmuSuNi/Roms o /mnt/usb0/Roms "
                            f"(intentado: {normalized})."
                        ) from exc
                    raise RuntimeError(f"Error FTP 550 creando '{part}' en '{cur_pwd}': {exc}") from exc
                raise
    return normalized


def test_ftp_target(target: dict[str, Any]) -> dict[str, Any]:
    host = str(target.get("host") or "").strip()
    port = int(target.get("port") or 21)
    user = str(target.get("username") or "anonymous").strip()
    password = str(target.get("password") or "")
    if not host:
        return {"ok": False, "message": "Falta la IP o hostname del destino."}

    ftp = ftplib.FTP()
    try:
        ftp.connect(host, port, timeout=4.0)
        ftp.login(user, password)
        pwd = ftp.pwd()
        candidates: list[str] = []
        for cand in [
            str(target.get("base_path") or "").strip(),
            "/data/EmuSuNi/Roms",
            "/mnt/usb0/Roms",
            "/data/retroarch/downloads",
            "/dev_hdd0/ROMS",
            "/roms",
        ]:
            if cand and cand not in candidates:
                candidates.append(cand)
        detected: list[str] = []
        for cand in candidates:
            try:
                ftp.cwd(cand)
                detected.append(cand)
            except ftplib.all_errors:
                continue
        ftp.quit()
        det_suffix = f" · Rutas detectadas: {', '.join(detected)}" if detected else ""
        return {
            "ok": True,
            "detectedPaths": detected,
            "message": f"Conectado a {host}:{port} (directorio raíz: {pwd}){det_suffix}",
        }
    except Exception as exc:
        try:
            ftp.close()
        except Exception:
            pass
        return {"ok": False, "message": f"Error FTP ({host}:{port}): {exc}"}


class TransferManager:
    """Background multi-console FTP upload queue."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._jobs: dict[str, dict[str, Any]] = {}

    def list_jobs(self) -> list[dict[str, Any]]:
        with self._lock:
            return [dict(j) for j in self._jobs.values()]

    def enqueue(
        self,
        rec: RomRecord,
        target: dict[str, Any],
        remote_dir_override: str = "",
        file_path_override: Path | None = None,
        remote_name_override: str = "",
        title_override: str = "",
    ) -> dict[str, Any]:
        local_file = file_path_override or rec.full_path
        if not local_file.is_file():
            raise FileNotFoundError(f"No existe el archivo en disco: {local_file}")

        job_id = str(uuid.uuid4())[:12]
        remote_dir = build_remote_dir(
            target,
            rec.console_key,
            raw_platform=rec.platform,
            remote_dir_override=remote_dir_override,
        )
        remote_name = (remote_name_override or local_file.name).strip()
        display_title = title_override or rec.title
        total_size = local_file.stat().st_size
        job = {
            "id": job_id,
            "entryId": rec.entry_id,
            "title": display_title,
            "targetId": target.get("id"),
            "targetName": target.get("name"),
            "remotePath": f"{remote_dir}/{remote_name}",
            "state": "queued",
            "bytes": 0,
            "total": total_size,
            "message": f"Encolado hacia {target.get('name')} ({target.get('host')}:{target.get('port')})",
        }
        with self._lock:
            self._jobs[job_id] = job

        t = threading.Thread(target=self._run_upload, args=(job_id, local_file, remote_dir, remote_name, target), daemon=True)
        t.start()
        return dict(job)

    def _run_upload(self, job_id: str, local_file: Path, remote_dir: str, remote_name: str, target: dict[str, Any]) -> None:
        host = str(target.get("host") or "").strip()
        port = int(target.get("port") or 21)
        user = str(target.get("username") or "anonymous").strip()
        password = str(target.get("password") or "")

        with self._lock:
            self._jobs[job_id]["state"] = "connecting"
            self._jobs[job_id]["message"] = f"Conectando a {host}:{port}…"

        ftp = ftplib.FTP()
        try:
            ftp.connect(host, port, timeout=10.0)
            ftp.login(user, password)
            try:
                ftp.set_pasv(True)
            except Exception:
                pass
            actual_dir = _ftp_makedirs(ftp, remote_dir)
            dest_display = f"{actual_dir.rstrip('/')}/{remote_name}"
            with self._lock:
                self._jobs[job_id]["state"] = "transferring"
                self._jobs[job_id]["remotePath"] = dest_display
                self._jobs[job_id]["message"] = f"Enviando a {dest_display}"

            def on_chunk(chunk: bytes) -> None:
                with self._lock:
                    self._jobs[job_id]["bytes"] += len(chunk)

            with local_file.open("rb") as fh:
                try:
                    ftp.storbinary(f"STOR {remote_name}", fh, blocksize=65536, callback=on_chunk)
                except ftplib.error_perm as exc:
                    if "550" in str(exc):
                        raise RuntimeError(
                            f"Error FTP 550 escribiendo {dest_display}: verifica permisos o espacio en la consola ({exc})"
                        ) from exc
                    raise
            ftp.quit()

            with self._lock:
                self._jobs[job_id]["state"] = "complete"
                self._jobs[job_id]["bytes"] = self._jobs[job_id]["total"]
                self._jobs[job_id]["message"] = f"Completado en {dest_display}"
        except Exception as exc:
            try:
                ftp.close()
            except Exception:
                pass
            with self._lock:
                self._jobs[job_id]["state"] = "failed"
                self._jobs[job_id]["message"] = str(exc)
