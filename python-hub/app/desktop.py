from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import threading
import time
import webbrowser
from pathlib import Path

import httpx

# Guarantee standard streams exist in PyInstaller noconsole/windowed builds
from app.config import DEFAULT_DATA_DIR, DEFAULT_PORT, resolve_default_roms_dir, resource_path

if sys.stdout is None:
    DEFAULT_DATA_DIR.mkdir(parents=True, exist_ok=True)
    _log_fh = (DEFAULT_DATA_DIR / "desktop-stdout.log").open("a", encoding="utf-8", buffering=1)
    sys.stdout = _log_fh
if sys.stderr is None:
    DEFAULT_DATA_DIR.mkdir(parents=True, exist_ok=True)
    _err_fh = (DEFAULT_DATA_DIR / "desktop-stderr.log").open("a", encoding="utf-8", buffering=1)
    sys.stderr = _err_fh

import uvicorn  # noqa: E402
from app.main import app  # noqa: E402


def _is_port_available(host: str, port: int) -> bool:
    check_host = "127.0.0.1" if host == "0.0.0.0" else host
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.4)
        return s.connect_ex((check_host, port)) != 0


def pick_port(host: str, preferred: int) -> int:
    if _is_port_available(host, preferred):
        return preferred
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return int(s.getsockname()[1])


def wait_for_health(port: int, timeout_s: float = 15.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last_err = ""
    while time.monotonic() < deadline:
        try:
            resp = httpx.get(f"http://127.0.0.1:{port}/health", timeout=1.5)
            if resp.status_code == 200:
                return resp.json()
        except Exception as exc:
            last_err = str(exc)
        time.sleep(0.2)
    raise RuntimeError(f"Servidor OzRetroRom no respondió a tiempo en puerto {port}: {last_err}")


def _find_chromium_app_browser() -> Path | None:
    candidates = [
        Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
        Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
        Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
        Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
    ]
    for p in candidates:
        if p.is_file():
            return p
    return None


def _resolve_desktop_icon() -> Path | None:
    for rel in (
        "app/static/assets/favicon.ico",
        "packaging/ozretrorom.ico",
        "app/static/assets/logo.png",
    ):
        candidate = resource_path(rel)
        if candidate.is_file():
            return candidate
    return None


def _launch_desktop_window(ui_url: str, data_dir: Path) -> None:
    try:
        import webview  # type: ignore

        icon_path = _resolve_desktop_icon()
        webview.create_window(
            "Game Vault",
            ui_url,
            width=1360,
            height=860,
            min_size=(900, 620),
            background_color="#0A0F0D",
        )
        if icon_path is not None:
            webview.start(icon=str(icon_path))
        else:
            webview.start()
        return
    except Exception:
        pass

    browser = _find_chromium_app_browser()
    if browser is not None:
        profile_dir = data_dir / "webapp-profile"
        profile_dir.mkdir(parents=True, exist_ok=True)
        proc = subprocess.Popen(
            [
                str(browser),
                f"--app={ui_url}",
                f"--user-data-dir={profile_dir}",
                "--window-size=1360,860",
                "--new-window",
            ]
        )
        proc.wait()
        return

    webbrowser.open(ui_url)
    try:
        while True:
            time.sleep(1.0)
    except KeyboardInterrupt:
        pass


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="OzRetroRom Desktop & Portable Hub")
    parser.add_argument("--host", default=os.environ.get("OZ_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--local-only", action="store_true")
    parser.add_argument("--server-only", action="store_true")
    parser.add_argument("--check-only", action="store_true")
    parser.add_argument("--check-file", default="")
    args = parser.parse_args(argv)

    host = "127.0.0.1" if (args.local_only or args.check_only) else args.host
    port = pick_port(host, args.port)

    Path(resolve_default_roms_dir()).mkdir(parents=True, exist_ok=True)
    DEFAULT_DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not args.server_only:
        os.environ["OZ_DESKTOP_MODE"] = "1"
    os.environ["OZ_ADVERTISED_PORT"] = str(port)
    os.environ["OZ_BIND_HOST"] = host
    if args.local_only or args.check_only:
        os.environ["OZ_DISABLE_MDNS"] = "1"

    config = uvicorn.Config(
        app,
        host=host,
        port=port,
        log_level="warning" if not args.server_only else "info",
        log_config=None,
    )
    server = uvicorn.Server(config)
    server_thread = threading.Thread(target=server.run, name="oz-uvicorn", daemon=True)
    server_thread.start()

    try:
        health = wait_for_health(port, timeout_s=20.0)
        if args.check_only:
            ok = health.get("status") == "ok" and int(health.get("minerva_entries") or 0) > 45000
            report = {"ok": ok, "port": port, "health": health}
            if args.check_file:
                out_p = Path(args.check_file)
                out_p.parent.mkdir(parents=True, exist_ok=True)
                out_p.write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(json.dumps(report))
            return 0 if ok else 1

        if args.server_only:
            print(f"OzRetroRom servidor activo en http://{host}:{port}/")
            try:
                while server_thread.is_alive():
                    time.sleep(0.5)
            except KeyboardInterrupt:
                pass
            return 0

        _launch_desktop_window(f"http://127.0.0.1:{port}/", DEFAULT_DATA_DIR)
        return 0
    finally:
        server.should_exit = True
        server_thread.join(timeout=3.0)


if __name__ == "__main__":
    raise SystemExit(main())
