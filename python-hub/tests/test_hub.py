from __future__ import annotations

import base64
import ftplib
import gzip
import hashlib
import http.server
import io
import json
import sqlite3
import struct
import threading
import time
import zipfile
import zlib
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.covers import _libretro_candidate_names, external_cover_id, optimize_to_webp
from app.db import HubDatabase
from app.ia_client import ia_auth_headers
from app.main import create_app
from app.minerva import MinervaOfflineIndex
from app.patcher import apply_bps, apply_ips
from app.transfers import _ftp_makedirs, build_remote_dir


def _make_sample_roms(root: Path) -> None:
    (root / "snes").mkdir(parents=True, exist_ok=True)
    (root / "snes" / "Super Mario World (USA).sfc").write_bytes(b"SNES_TEST_ROM_PAYLOAD")
    (root / "genesis").mkdir(parents=True, exist_ok=True)
    (root / "genesis" / "Sonic the Hedgehog (USA).md").write_bytes(b"MD_TEST_ROM_PAYLOAD")
    (root / "ps1").mkdir(parents=True, exist_ok=True)
    (root / "ps1" / "Resident Evil (USA).chd").write_bytes(b"PS1_RESIDENT_PAYLOAD")
    (root / "ps1" / "Resident Evil 2 (USA).chd").write_bytes(b"PS1_RESIDENT2_PAYLOAD")
    (root / "switch").mkdir(parents=True, exist_ok=True)
    (root / "switch" / "Homebrew Demo (World).nsp").write_bytes(b"NSP_TEST_PAYLOAD")
    (root / "arcade").mkdir(parents=True, exist_ok=True)
    (root / "arcade" / "xmvsf.zip").write_bytes(b"ARCADE_XMVSF_PAYLOAD")
    (root / "arcade" / "kinst.zip.ozpart").write_bytes(b"PARTIAL")
    (root / "arcade" / "mvs.01").write_bytes(b"CHIP")
    (root / "snes" / "systeminfo.txt").write_text("config stub", encoding="utf-8")


def test_python_hub_endpoints_and_webp(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    data_dir = tmp_path / "data"
    _make_sample_roms(roms_dir)

    # Pre-seed an older DB without last_played_at/play_count to verify safe migration
    data_dir.mkdir(parents=True, exist_ok=True)
    legacy_conn = sqlite3.connect(str(data_dir / "oz_hub.db"))
    legacy_conn.execute(
        """
        CREATE TABLE rom_meta (
            entry_id TEXT PRIMARY KEY,
            sha256 TEXT,
            verified_at TEXT,
            favorite INTEGER NOT NULL DEFAULT 0,
            cover_status TEXT,
            cover_source TEXT,
            cover_updated_at TEXT
        );
        """
    )
    legacy_conn.commit()
    legacy_conn.close()

    mock_payload = b"PS2_ISO_FAKE_PAYLOAD_1234567890"
    captured_auth: dict[str, str] = {}

    def _mock_handler(request: httpx.Request) -> httpx.Response:
        captured_auth["auth"] = request.headers.get("authorization", "")
        return httpx.Response(
            200,
            content=mock_payload,
            headers={"content-length": str(len(mock_payload))},
        )

    app = create_app(
        data_dir=data_dir,
        roms_dir_override=str(roms_dir),
        download_transport=httpx.MockTransport(_mock_handler),
    )
    with TestClient(app) as client:
        # 1. Health check includes native MiNERVA + MAME shard count (> 46k)
        h = client.get("/health")
        assert h.status_code == 200
        health_json = h.json()
        assert health_json["status"] == "ok"
        assert health_json["engine"] == "python-fastapi"
        assert health_json["roms_count"] == 9
        assert health_json["minerva_entries"] > 46000
        assert health_json["mame_entries"] > 2600

        # 1b. Game Vault branding in root HTML (no "Python Hub", "Oz RetroRom" or "Arcade Vault" label)
        html_home = client.get("/")
        assert html_home.status_code == 200
        assert "GAME VAULT" in html_home.text
        assert "RETRO EDITION" in html_home.text
        assert "python hub" not in html_home.text.lower()
        assert "arcade vault" not in html_home.text.lower()
        assert "oz retrorom" not in html_home.text.lower()

        # 2. System info endpoint
        sys_info = client.get("/api/v1/system/info")
        assert sys_info.status_code == 200
        assert sys_info.json()["mameEntries"] > 2600
        assert sys_info.json()["lanIp"]

        # 3. Library scan + arcade metadata enrichment + non-ROM/chip artifact hiding
        lib = client.get("/api/v1/library").json()
        titles = {x["title"]: x for x in lib}
        by_rel = {x["relativePath"]: x for x in lib}
        assert "Sonic the Hedgehog (USA)" in titles
        assert titles["Sonic the Hedgehog (USA)"]["isSys"] is False
        assert titles["Sonic the Hedgehog (USA)"]["consoleKey"] == "genesis"
        assert titles["systeminfo"]["isSys"] is True
        xmvsf_row = by_rel["xmvsf.zip"]
        assert "X-Men Vs. Street Fighter" in xmvsf_row["title"]
        assert xmvsf_row["hasMetadata"] is True
        assert xmvsf_row["romset"] == "xmvsf"
        assert xmvsf_row["region"] == "Europe"
        assert xmvsf_row["isSys"] is False
        assert by_rel["kinst.zip.ozpart"]["isSys"] is True
        assert by_rel["mvs.01"]["isSys"] is True

        # 4. Unified Arcade search (FBNeo + MAME) & MAME-only filter
        kinst_hits = client.get("/api/v1/catalog/search", params={"q": "killer instinct", "platform": "arcade"})
        assert kinst_hits.status_code == 200
        kinst_rows = kinst_hits.json()
        assert len(kinst_rows) > 0
        assert any(r.get("identifier") == "killerinstinct_arcade" for r in kinst_rows)
        assert any(r.get("arcadeCore") == "MAME" for r in kinst_rows)

        mk_hits = client.get("/api/v1/catalog/search", params={"q": "mortal kombat", "platform": "arcade"})
        assert mk_hits.status_code == 200
        mk_rows = mk_hits.json()
        assert any(r.get("arcadeCore") == "FBNeo" for r in mk_rows)
        assert any(r.get("arcadeCore") == "MAME" for r in mk_rows)

        mame_tekken = client.get("/api/v1/catalog/search", params={"q": "tekken", "platform": "mame"})
        assert mame_tekken.status_code == 200
        assert any("tekken" in r["title"].lower() and r.get("arcadeCore") == "MAME" for r in mame_tekken.json())

        snes_hits = client.get("/api/v1/catalog/search", params={"q": "mario", "platform": "snes"})
        assert snes_hits.status_code == 200
        assert len(snes_hits.json()) > 0

        ps1_hits = client.get("/api/v1/catalog/search", params={"q": "resident", "platform": "ps1"})
        assert ps1_hits.status_code == 200
        assert len(ps1_hits.json()) > 0

        # 5. Favorite toggle, Played tracking (migrated DB) & SHA-256 verification
        sonic_id = titles["Sonic the Hedgehog (USA)"]["entryId"]
        fav1 = client.post(f"/api/v1/library/{sonic_id}/favorite")
        assert fav1.status_code == 200
        assert fav1.json()["favorite"] is True

        p1 = client.post(f"/api/v1/library/{sonic_id}/played")
        assert p1.status_code == 200
        assert p1.json()["playCount"] == 1
        assert p1.json()["lastPlayedAt"]

        p2 = client.post(f"/api/v1/library/{sonic_id}/played")
        assert p2.status_code == 200
        assert p2.json()["playCount"] == 2

        lib_played = client.get("/api/v1/library").json()
        sonic_after = next(x for x in lib_played if x["entryId"] == sonic_id)
        assert sonic_after["playCount"] == 2
        assert sonic_after["lastPlayedAt"] is not None

        ver = client.post(f"/api/v1/library/{sonic_id}/verify")
        assert ver.status_code == 200
        assert len(ver.json()["sha256"]) == 64

        # 6. Settings GET/PUT, onboarding default, console_paths, IA S3 headers & Targets PS4/PS3/Switch overrides
        st = client.get("/api/v1/settings").json()
        assert st["onboarding_completed"] is False
        assert st["covers_dir"] == ""
        assert st["console_paths"]["mame"] == "arcade"
        assert len(st["targets"]) == 3
        assert {"ps4-emusuni", "ps3-webman", "switch-ftp"} <= {t["id"] for t in st["targets"]}

        put = client.put(
            "/api/v1/settings",
            json={
                "steamgriddb_api_key": "test-key-123",
                "ia_access_key": "my_access",
                "ia_secret_key": "my_secret",
            },
        )
        assert put.status_code == 200
        assert put.json()["steamgriddb_api_key"] == "test-key-123"
        assert ia_auth_headers(put.json())["Authorization"] == "LOW my_access:my_secret"

        # 6b. Test per-console FTP path_overrides persistence and resolution priority
        ps4_target = next(t for t in st["targets"] if t["id"] == "ps4-emusuni")
        assert build_remote_dir(ps4_target, "arcade", raw_platform="mame") == "/data/EmuSuNi/Roms/arcade"
        ps4_target["path_overrides"]["mame"] = "/data/EmuSuNi/Roms/mame_custom"
        put_tgt = client.put("/api/v1/targets/ps4-emusuni", json=ps4_target)
        assert put_tgt.status_code == 200
        targets_after = client.get("/api/v1/targets").json()
        ps4_after = next(t for t in targets_after if t["id"] == "ps4-emusuni")
        assert ps4_after["path_overrides"]["mame"] == "/data/EmuSuNi/Roms/mame_custom"
        assert build_remote_dir(ps4_after, "arcade", raw_platform="mame") == "/data/EmuSuNi/Roms/mame_custom"
        assert build_remote_dir(ps4_after, "arcade", raw_platform="arcade") == "/data/EmuSuNi/Roms/arcade"
        assert build_remote_dir(ps4_after, "arcade", raw_platform="mame", remote_dir_override="Custom/Dir/") == "Custom/Dir"

        # 6c. MAME download saves under configured mame local path (default 'arcade', plus external absolute dir)
        mame_dl = client.post(
            "/api/v1/downloads/ia",
            json={
                "identifier": "killerinstinct_arcade",
                "filename": "kinst.zip",
                "platform": "arcade",
                "title": "Killer Instinct",
                "arcadeCore": "MAME",
            },
        )
        assert mame_dl.status_code == 200
        mame_job_id = mame_dl.json()["id"]
        cur = None
        for _ in range(30):
            jobs = client.get("/api/v1/downloads").json()
            cur = next((j for j in jobs if j["id"] == mame_job_id), None)
            if cur and cur["state"] == "complete":
                break
            time.sleep(0.05)
        assert cur is not None and cur["state"] == "complete"
        assert (roms_dir / "arcade" / "kinst.zip").read_bytes() == mock_payload

        ext_mame_dir = tmp_path / "external_mame_roms"
        new_paths = dict(st["console_paths"])
        new_paths["mame"] = str(ext_mame_dir)
        client.put("/api/v1/settings", json={"console_paths": new_paths})
        mame_ext_dl = client.post(
            "/api/v1/downloads/ia",
            json={
                "identifier": "killerinstinct2_arcade",
                "filename": "kinst2.zip",
                "platform": "arcade",
                "title": "Killer Instinct 2",
                "arcadeCore": "MAME",
            },
        )
        assert mame_ext_dl.status_code == 200
        ext_job_id = mame_ext_dl.json()["id"]
        for _ in range(30):
            jobs = client.get("/api/v1/downloads").json()
            cur = next((j for j in jobs if j["id"] == ext_job_id), None)
            if cur and cur["state"] == "complete":
                break
            time.sleep(0.05)
        assert cur is not None and cur["state"] == "complete"
        assert (ext_mame_dir / "kinst2.zip").read_bytes() == mock_payload
        lib_with_ext = client.get("/api/v1/library").json()
        assert any(
            x.get("romset") == "kinst2" and "Killer Instinct 2" in x["title"] and x["consoleKey"] == "arcade"
            for x in lib_with_ext
        )

        # 7. Direct in-app IA download streams with LOW auth header and updates library
        dl = client.post(
            "/api/v1/downloads/ia",
            json={
                "identifier": "test-ps2-item",
                "filename": "test_game.iso",
                "platform": "ps2",
                "title": "Test PS2 Game",
            },
        )
        assert dl.status_code == 200
        job_id = dl.json()["id"]
        cur = None
        for _ in range(30):
            jobs = client.get("/api/v1/downloads").json()
            cur = next((j for j in jobs if j["id"] == job_id), None)
            if cur and cur["state"] == "complete":
                break
            time.sleep(0.05)
        assert cur is not None and cur["state"] == "complete"
        assert captured_auth["auth"] == "LOW my_access:my_secret"
        assert (roms_dir / "ps2" / "test_game.iso").read_bytes() == mock_payload
        lib_after = client.get("/api/v1/library").json()
        assert any(x["title"] == "test_game" and x["consoleKey"] == "ps2" for x in lib_after)

        # 8. Tinfoil index
        tf = client.get("/tinfoil/index.json")
        assert tf.status_code == 200
        assert tf.json()["files"][0]["url"].endswith("Homebrew%20Demo%20%28World%29.nsp")

        # 9. Cover fallback & targeted cover sync
        assert client.get(f"/api/v1/covers/{sonic_id}.webp").status_code == 200
        res_ids = [titles["Resident Evil (USA)"]["entryId"], titles["Resident Evil 2 (USA)"]["entryId"]]
        client.put("/api/v1/settings", json={"steamgriddb_api_key": "", "enable_libretro_fallback": False})
        sync = client.post("/api/v1/covers/sync", json={"entryIds": res_ids, "limit": 24})
        assert sync.status_code == 200
        assert sync.json()["total"] == 2

        # 10. External cover resolve-batch
        ext_key = "ps1:resident evil"
        ext_id = external_cover_id(ext_key)
        img = Image.new("RGB", (400, 600), color=(30, 80, 140))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        webp_bytes = optimize_to_webp(buf.getvalue(), max_width=260, quality=76)
        (data_dir / "covers" / f"{ext_id}.webp").write_bytes(webp_bytes)
        rb = client.post(
            "/api/v1/covers/resolve-batch",
            json={"items": [{"key": ext_key, "title": "Resident Evil", "consoleKey": "ps1"}]},
        )
        assert rb.status_code == 200
        assert rb.json()["covers"][ext_key] == f"/api/v1/covers/{ext_id}.webp"

    assert "4x4 Evo (USA)" in _libretro_candidate_names("4×4 Evo (USA)")
    assert "Killer Instinct" in _libretro_candidate_names("Killer Instinct (v1.5d) (MAME / Arcade)", console_key="arcade")
    assert len(webp_bytes) < 25000


def test_minerva_expansion_id_matches_java_format(tmp_path: Path) -> None:
    shards_dir = tmp_path / "minerva_shards"
    shards_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "collections": [
            {
                "platform": "ps1",
                "infoHash": "abc123",
                "directory": "Redump_PS1",
                "files": [
                    {
                        "index": 7,
                        "filename": "Chrono Cross (USA).chd",
                        "title": "Chrono Cross",
                        "size": 123456,
                    }
                ],
            }
        ]
    }
    (shards_dir / "minerva-expansion.json.gz.bin").write_bytes(
        gzip.compress(json.dumps(payload).encode("utf-8"))
    )
    idx = MinervaOfflineIndex(shard_dirs=[shards_dir], jar_fallback=tmp_path / "missing.jar")
    idx.load()
    hits = idx.search("chrono cross", platform="ps1")
    expected_id = hashlib.sha256(
        b"minerva\x00ps1\x00abc123\x007\x00Redump_PS1/Chrono Cross (USA).chd"
    ).hexdigest()
    assert len(hits) == 1
    assert hits[0]["id"] == expected_id


def test_java_proxy_strips_origin_and_referer(tmp_path: Path) -> None:
    captured_headers: dict[str, str] = {}

    class _Handler(http.server.BaseHTTPRequestHandler):
        def do_POST(self) -> None:
            for k, v in self.headers.items():
                captured_headers[k.lower()] = v
            length = int(self.headers.get("Content-Length", "0"))
            if length:
                self.rfile.read(length)
            body = json.dumps({"id": "dl-1", "state": "queued"}).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format: str, *args: object) -> None:
            return

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        roms_dir = tmp_path / "roms"
        roms_dir.mkdir(parents=True, exist_ok=True)
        app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
        with TestClient(app) as client:
            client.put("/api/v1/settings", json={"java_companion_url": f"http://127.0.0.1:{port}"})
            resp = client.post(
                "/oz/v1/minerva/downloads",
                json={"id": "cand-1", "provider": "MiNERVA"},
                headers={
                    "Origin": "http://192.168.100.173:17894",
                    "Referer": "http://192.168.100.173:17894/",
                },
            )
            assert resp.status_code == 200
            assert "origin" not in captured_headers
            assert "referer" not in captured_headers
    finally:
        server.shutdown()
        server.server_close()


def test_legacy_ps4_target_migrates_from_relative_roms(tmp_path: Path) -> None:
    db_path = tmp_path / "oz_hub.db"
    conn = sqlite3.connect(str(db_path))
    conn.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    legacy_targets = [
        {
            "id": "ps4-emusuni",
            "name": "PS4 (EmuSuNi)",
            "console": "PS4",
            "protocol": "ftp",
            "host": "192.168.100.141",
            "port": 2121,
            "username": "anonymous",
            "password": "anonymous@oz.local",
            "base_path": "Roms",
            "path_overrides": {"arcade": "Roms/arcade", "snes": "/mnt/usb0/Roms/snes"},
            "notes": "Fork EmuSuNi o servidor FTP GoldHEN en PS4.",
        }
    ]
    conn.execute(
        "INSERT INTO settings (key, value) VALUES (?, ?)",
        ("targets", json.dumps(legacy_targets)),
    )
    conn.commit()
    conn.close()

    db = HubDatabase(db_path)
    try:
        migrated = db.get_settings()["targets"][0]
        assert migrated["base_path"] == "/data/EmuSuNi/Roms"
        assert migrated["path_overrides"]["arcade"] == "/data/EmuSuNi/Roms/arcade"
        # Custom absolute overrides must be preserved untouched
        assert migrated["path_overrides"]["snes"] == "/mnt/usb0/Roms/snes"
    finally:
        db.close()


def test_ftp_makedirs_resolves_goldhen_readonly_root() -> None:
    class _FakeGoldHenFtp:
        def __init__(self, existing_dirs: set[str]) -> None:
            self._cwd = "/"
            self._dirs = set(existing_dirs)

        def pwd(self) -> str:
            return self._cwd

        def cwd(self, path: str) -> None:
            if path.startswith("/"):
                norm = path.rstrip("/") or "/"
            else:
                base = "" if self._cwd == "/" else self._cwd.rstrip("/")
                norm = f"{base}/{path}".rstrip("/") or "/"
            if norm not in self._dirs:
                raise ftplib.error_perm("550 Requested action not taken.")
            self._cwd = norm

        def mkd(self, part: str) -> None:
            if self._cwd == "/":
                raise ftplib.error_perm("550 Requested action not taken.")
            base = self._cwd.rstrip("/")
            self._dirs.add(f"{base}/{part}")

    ftp_ok = _FakeGoldHenFtp({"/", "/data", "/data/EmuSuNi", "/data/EmuSuNi/Roms", "/data/EmuSuNi/Roms/arcade"})
    resolved = _ftp_makedirs(ftp_ok, "Roms/arcade")  # type: ignore[arg-type]
    assert resolved == "/data/EmuSuNi/Roms/arcade"

    ftp_fail = _FakeGoldHenFtp({"/"})
    with pytest.raises(RuntimeError) as exc_info:
        _ftp_makedirs(ftp_fail, "Roms/arcade")  # type: ignore[arg-type]
    msg = str(exc_info.value)
    assert "550" in msg
    assert "/data/EmuSuNi/Roms" in msg


def test_onboarding_settings_inspect_and_custom_covers_dir(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    custom_covers = tmp_path / "custom_covers"
    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        insp_ok = client.post("/api/v1/system/inspect-path", json={"path": str(roms_dir)}).json()
        assert insp_ok["valid"] is True and insp_ok["exists"] is True

        insp_new = client.post("/api/v1/system/inspect-path", json={"path": str(tmp_path / "new_roms")}).json()
        assert insp_new["valid"] is True and insp_new["exists"] is False

        put = client.put(
            "/api/v1/settings",
            json={"covers_dir": str(custom_covers), "onboarding_completed": True},
        )
        assert put.status_code == 200
        assert put.json()["onboarding_completed"] is True
        assert put.json()["covers_dir"] == str(custom_covers)

        img = Image.new("RGB", (200, 300), color=(255, 176, 0))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        (custom_covers / "custom-cover-test.webp").write_bytes(optimize_to_webp(buf.getvalue()))

        cov_resp = client.get("/api/v1/covers/custom-cover-test.webp")
        assert cov_resp.status_code == 200
        assert client.get("/api/v1/covers/status").json()["cachedCount"] >= 1
        assert client.get("/api/v1/system/info").json()["coversDir"] == str(custom_covers)


def test_existing_db_upgrades_with_onboarding_completed_true(tmp_path: Path) -> None:
    db_path = tmp_path / "oz_hub.db"
    conn = sqlite3.connect(str(db_path))
    conn.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    conn.execute(
        "INSERT INTO settings (key, value) VALUES (?, ?)",
        ("roms_dir", json.dumps(str(tmp_path / "existing_roms"))),
    )
    conn.commit()
    conn.close()

    db = HubDatabase(db_path)
    try:
        assert db.get_settings()["onboarding_completed"] is True
    finally:
        db.close()


def test_custom_cover_upload_and_home_personalization_settings(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        lib = client.get("/api/v1/library").json()
        sonic = next(x for x in lib if "Sonic" in x["title"])

        img = Image.new("RGB", (240, 320), color=(0, 229, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        b64 = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")

        up = client.post(f"/api/v1/covers/custom/{sonic['entryId']}", json={"imageDataUrl": b64})
        assert up.status_code == 200
        up_json = up.json()
        assert up_json["status"] == "found"
        assert up_json["source"] == "custom"
        assert "?v=" in up_json["coverUrl"]

        lib_after = client.get("/api/v1/library").json()
        sonic_after = next(x for x in lib_after if x["entryId"] == sonic["entryId"])
        assert sonic_after["hasCover"] is True
        assert sonic_after["coverSource"] == "custom"
        assert "?v=" in (sonic_after["coverUrl"] or "")

        custom_order = ["recommendations", "featured", "recent", "favorites", "consoles"]
        put = client.put(
            "/api/v1/settings",
            json={"pinned_hero_entry_id": sonic["entryId"], "home_section_order": custom_order},
        )
        assert put.status_code == 200
        s = client.get("/api/v1/settings").json()
        assert s["pinned_hero_entry_id"] == sonic["entryId"]
        assert s["home_section_order"] == custom_order


def test_local_recommendations_and_ai_settings(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    (roms_dir / "snes" / "Super Mario RPG (USA).sfc").write_bytes(b"SMRPG" * 64)
    (roms_dir / "snes" / "Chrono Trigger (USA).sfc").write_bytes(b"CHRONO" * 64)
    (roms_dir / "ps1" / "Final Fantasy IX (USA).chd").write_bytes(b"FFIX" * 64)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        lib = client.get("/api/v1/library").json()
        smrpg = next(x for x in lib if "Super Mario RPG" in x["title"])
        assert client.post(f"/api/v1/library/{smrpg['entryId']}/played").status_code == 200
        assert client.post(f"/api/v1/library/{smrpg['entryId']}/favorite").status_code == 200

        recs_resp = client.get("/api/v1/recommendations?limit=6")
        assert recs_resp.status_code == 200
        recs = recs_resp.json()
        assert recs["engine"] == "local"
        assert recs["gameOfTheWeek"] and recs["gameOfTheWeek"]["entryId"]
        assert len(recs["fromLibrary"]) > 0
        assert len(recs["discoverCatalog"]) > 0
        assert "RPG / JRPG" in recs["tasteProfile"]

        ai_ping = client.post("/api/v1/settings/test-ai", json={"provider": "local"})
        assert ai_ping.status_code == 200
        assert ai_ping.json()["ok"] is True


def _encode_bps_vlq(val: int) -> bytes:
    out = bytearray()
    while True:
        x = val & 0x7F
        val >>= 7
        if val == 0:
            out.append(0x80 | x)
            return bytes(out)
        out.append(x)
        val -= 1


def _build_test_bps1(source: bytes, target: bytes) -> bytes:
    buf = bytearray(b"BPS1")
    buf.extend(_encode_bps_vlq(len(source)))
    buf.extend(_encode_bps_vlq(len(target)))
    buf.extend(_encode_bps_vlq(0))  # metadata size
    # Encode whole target via TargetRead action (mode=1)
    buf.extend(_encode_bps_vlq(((len(target) - 1) << 2) | 1))
    buf.extend(target)
    buf.extend(struct.pack("<II", zlib.crc32(source) & 0xFFFFFFFF, zlib.crc32(target) & 0xFFFFFFFF))
    patch_crc = zlib.crc32(buf) & 0xFFFFFFFF
    buf.extend(struct.pack("<I", patch_crc))
    return bytes(buf)


def test_ips_and_bps_patcher_direct_apply_download_and_snes_header(tmp_path: Path) -> None:
    src_rom = b"HELLO WORLD " + (b"\x00" * 1012)  # 1024 bytes (clean unheadered SNES size multiple)
    # Test pure-Python apply_ips (normal + RLE)
    ips = (
        b"PATCH"
        + (0).to_bytes(3, "big") + (5).to_bytes(2, "big") + b"HOLA!"
        + (12).to_bytes(3, "big") + (0).to_bytes(2, "big") + (4).to_bytes(2, "big") + b"X"
        + b"EOF"
    )
    ips_out = apply_ips(src_rom, ips)
    assert ips_out[:5] == b"HOLA!"
    assert ips_out[12:16] == b"XXXX"

    # Test pure-Python apply_bps + SNES 512-byte SMC header stripping
    target_es = b"MUNDO ES    " + (b"\x11" * 1012)
    bps = _build_test_bps1(src_rom, target_es)
    smc_headered = (b"\xFF" * 512) + src_rom
    bps_out, bps_meta = apply_bps(smc_headered, bps, console_key="snes")
    assert bps_out == target_es
    assert bps_meta["headerShift"] == -512

    # End-to-end API apply (zipped BPS) + download + original ROM untouched
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    snes_file = roms_dir / "snes" / "Chrono Trigger (USA).sfc"
    snes_file.write_bytes(src_rom)

    zbuf = io.BytesIO()
    with zipfile.ZipFile(zbuf, "w") as zf:
        zf.writestr("ChronoTrigger_ES.bps", bps)
    b64_zip = "data:application/zip;base64," + base64.b64encode(zbuf.getvalue()).decode("ascii")

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        lib = client.get("/api/v1/library").json()
        ct = next(x for x in lib if "Chrono Trigger" in x["title"])

        res = client.post(
            f"/api/v1/patches/apply/{ct['entryId']}",
            json={"patchDataBase64": b64_zip, "patchFilename": "traduccion_es.zip", "label": "Chrono Trigger [ES]"},
        )
        assert res.status_code == 200
        pid = res.json()["patch"]["patch_id"]
        assert res.json()["patch"]["patch_format"] == "BPS"

        dl_es = client.get(f"/api/v1/patches/download/{pid}")
        assert dl_es.status_code == 200
        assert dl_es.content == target_es

        dl_via_lib = client.get(f"/api/v1/library/{ct['entryId']}/download", params={"patch_id": pid})
        assert dl_via_lib.status_code == 200
        assert dl_via_lib.content == target_es

        dl_orig = client.get(f"/api/v1/library/{ct['entryId']}/download")
        assert dl_orig.status_code == 200
        assert dl_orig.content == src_rom
        assert snes_file.read_bytes() == src_rom


def test_sagas_grouping_custom_saga_and_mood_picker(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    (roms_dir / "ps1" / "Resident Evil 2 (USA).chd").write_bytes(b"RE2" * 64)
    (roms_dir / "ps1" / "Resident Evil 3 - Nemesis (USA).chd").write_bytes(b"RE3" * 64)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        sagas = client.get("/api/v1/sagas").json()
        re_saga = next(s for s in sagas if s["id"] == "resident-evil")
        assert re_saga["ownedCount"] >= 2
        assert re_saga["totalCount"] >= 6
        missing = [e for e in re_saga["entries"] if not e["owned"]]
        assert len(missing) >= 1 and missing[0]["minervaQuery"]

        lib = client.get("/api/v1/library").json()
        sonic = next(x for x in lib if "Sonic" in x["title"])
        created = client.post(
            "/api/v1/sagas/custom",
            json={"name": "Favoritos Arcade de Fin de Semana", "tagline": "Cortos y directos", "entryIds": [sonic["entryId"]]},
        )
        assert created.status_code == 200
        assert any(s["name"] == "Favoritos Arcade de Fin de Semana" and s["ownedCount"] == 1 for s in created.json())

        mood = client.get("/api/v1/recommendations/mood", params={"mood": "quick", "offset": 0})
        assert mood.status_code == 200
        assert mood.json()["durationHint"] == "15–30 min"
        assert len(mood.json()["picks"]) >= 1


def test_game_description_resolution_and_sqlite_cache(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        lib = client.get("/api/v1/library").json()
        sonic = next(x for x in lib if "Sonic" in x["title"])

        d = client.get(f"/api/v1/library/{sonic['entryId']}/description")
        assert d.status_code == 200
        dj = d.json()
        assert "Green Hill" in dj["description"]
        assert dj["releaseYear"] == "1991"
        assert dj["saga"] and dj["saga"]["id"] == "sonic"

        lib_after = client.get("/api/v1/library").json()
        sonic_after = next(x for x in lib_after if x["entryId"] == sonic["entryId"])
        assert "Green Hill" in sonic_after["description"]


def test_sagas_strict_matching_chronology_and_history(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    _make_sample_roms(roms_dir)
    # Potential false positives and multi-era entries that previously broke Sagas ordering/grouping
    (roms_dir / "snes" / "Aerobiz Supersonic (USA).sfc").write_bytes(b"AEROBIZ" * 16)
    (roms_dir / "gba").mkdir(parents=True, exist_ok=True)
    (roms_dir / "gba" / "Dragon Ball Z - Supersonic Warriors (Europe).gba").write_bytes(b"DBZ" * 16)
    (roms_dir / "snes" / "Sonic Blast Man (USA).sfc").write_bytes(b"SBM" * 16)
    (roms_dir / "genesis" / "Sonic The Hedgehog 2 (World).md").write_bytes(b"SONIC2" * 16)
    (roms_dir / "genesis" / "Sonic The Hedgehog 3 (USA).md").write_bytes(b"SONIC3" * 16)
    (roms_dir / "genesis" / "Sonic 3D Blast (USA).md").write_bytes(b"SONIC3D" * 16)
    (roms_dir / "nes").mkdir(parents=True, exist_ok=True)
    (roms_dir / "nes" / "Mega Man (USA).nes").write_bytes(b"MM1" * 16)
    (roms_dir / "nes" / "Mega Man 2 (USA).nes").write_bytes(b"MM2" * 16)
    (roms_dir / "snes" / "Mega Man X.sfc").write_bytes(b"MMX" * 16)
    (roms_dir / "snes" / "Mega Man X2 (USA).sfc").write_bytes(b"MMX2" * 16)
    (roms_dir / "gba" / "Megaman Zero 1 [E].gba").write_bytes(b"MMZ1" * 16)
    (roms_dir / "snes" / "Akumajou Dracula (Japan).sfc").write_bytes(b"SCV4" * 16)
    (roms_dir / "genesis" / "Castlevania - The New Generation (Europe).md").write_bytes(b"CVBL" * 16)
    (roms_dir / "genesis" / "Bram Stoker's Dracula (USA).md").write_bytes(b"BRAM" * 16)
    (roms_dir / "wii").mkdir(parents=True, exist_ok=True)
    (roms_dir / "wii" / "New Super Mario Bros. Wii (USA).wbfs").write_bytes(b"NSMBW" * 16)
    (roms_dir / "gba" / "Kirby & The Amazing Mirror (USA).gba").write_bytes(b"KIRBYAM" * 16)

    covers_dir = tmp_path / "data" / "covers"
    covers_dir.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGB", (480, 270), color=(40, 120, 240))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    (covers_dir / "saga-hero-sonic.webp").write_bytes(optimize_to_webp(buf.getvalue(), max_width=400))
    (covers_dir / "saga-key-sonic.webp").write_bytes(optimize_to_webp(buf.getvalue(), max_width=240))

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        sagas = client.get("/api/v1/sagas").json()
        by_id = {s["id"]: s for s in sagas}

        # Sonic excludes Aerobiz Supersonic, DBZ Supersonic Warriors & Sonic Blast Man and stays strictly chronological
        sonic_s = by_id["sonic"]
        sonic_titles = [e["title"].lower() for e in sonic_s["entries"]]
        assert not any("aerobiz" in t or "dragon ball" in t or "blast man" in t for t in sonic_titles)
        s1_idx = next(i for i, e in enumerate(sonic_s["entries"]) if e["title"] == "Sonic the Hedgehog")
        s2_idx = next(i for i, e in enumerate(sonic_s["entries"]) if e["title"] == "Sonic the Hedgehog 2")
        s3_idx = next(i for i, e in enumerate(sonic_s["entries"]) if e["title"] == "Sonic the Hedgehog 3")
        s3d_idx = next(i for i, e in enumerate(sonic_s["entries"]) if e["title"] == "Sonic 3D Blast")
        assert s1_idx < s2_idx < s3_idx < s3d_idx
        assert sonic_s["heroBannerUrl"] and "saga-hero-sonic.webp" in sonic_s["heroBannerUrl"]
        assert sonic_s["keyArtUrl"] and "saga-key-sonic.webp" in sonic_s["keyArtUrl"]

        # Mega Man orders MM1 < MM2 < MMX < MMX2 < MM Zero and detects extensionless Mega Man X / Megaman Zero 1
        mm_s = by_id["mega-man"]
        mm_map = {e["title"]: e for e in mm_s["entries"]}
        assert mm_map["Mega Man"]["owned"] is True
        assert mm_map["Mega Man 2"]["owned"] is True
        assert mm_map["Mega Man X"]["owned"] is True
        assert mm_map["Mega Man Zero"]["owned"] is True
        assert mm_map["Mega Man"]["order"] < mm_map["Mega Man 2"]["order"] < mm_map["Mega Man X"]["order"] < mm_map["Mega Man X2"]["order"] < mm_map["Mega Man Zero"]["order"]

        # Castlevania excludes Bram Stoker's Dracula, has researched depth >= 12, and maps Akumajou Dracula / New Generation
        cv_s = by_id["castlevania"]
        cv_titles = [e["title"].lower() for e in cv_s["entries"]]
        assert not any("bram stoker" in t for t in cv_titles)
        assert len(cv_s["entries"]) >= 12
        cv_map = {e["title"]: e for e in cv_s["entries"]}
        assert cv_map["Super Castlevania IV"]["owned"] is True
        assert cv_map["Castlevania: Bloodlines"]["owned"] is True

        # Super Mario doesn't match New Super Mario Bros. Wii as 1985 Super Mario Bros.
        mario_map = {e["title"]: e for e in by_id["super-mario"]["entries"]}
        assert mario_map["Super Mario Bros."]["owned"] is False
        assert mario_map["New Super Mario Bros. Wii"]["owned"] is True

        # Every curated saga carries rich franchise history + foundedYear + creator
        for s in sagas:
            if not s["isCustom"]:
                assert len(s.get("history", "")) >= 80
                assert s.get("foundedYear")
                assert s.get("creator")

        # Resolve-art returns cached URLs cleanly offline
        art_resp = client.post("/api/v1/sagas/resolve-art", json={"sagaIds": ["sonic"]})
        assert art_resp.status_code == 200
        assert "sonic" in art_resp.json()["art"]

        # Normalized curated lookup matches titles with &
        lib = client.get("/api/v1/library").json()
        kirby = next(x for x in lib if "Amazing Mirror" in x["title"])
        kd = client.get(f"/api/v1/library/{kirby['entryId']}/description").json()
        assert kd["source"] == "curated-es"
        assert "espejo" in kd["description"].lower()


def test_library_engine_deduplication_and_maintenance(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    (roms_dir / "genesis").mkdir(parents=True, exist_ok=True)
    (roms_dir / "dc").mkdir(parents=True, exist_ok=True)
    (roms_dir / "gba").mkdir(parents=True, exist_ok=True)

    # Same-game region / beta variants on Genesis + Japanese alias
    (roms_dir / "genesis" / "Sonic The Hedgehog (USA, Europe).md").write_bytes(b"US" * 32)
    (roms_dir / "genesis" / "Sonic The Hedgehog (Japan).md").write_bytes(b"JP" * 32)
    (roms_dir / "genesis" / "Sonic The Hedgehog (USA) (Beta).md").write_bytes(b"BT" * 32)
    (roms_dir / "genesis" / "Revenge of Shinobi, The (USA, Europe).md").write_bytes(b"SHINOBI_US" * 16)
    (roms_dir / "genesis" / "Super Shinobi, The (Japan).md").write_bytes(b"SHINOBI_JP" * 16)

    # Multi-disc playlist + individual discs on Dreamcast
    (roms_dir / "dc" / "Alone in the Dark - The New Nightmare.m3u").write_bytes(b"Disc 1.chd\nDisc 2.chd\n")
    (roms_dir / "dc" / "Alone in the Dark - The New Nightmare (USA) (Disc 1).chd").write_bytes(b"D1" * 64)
    (roms_dir / "dc" / "Alone in the Dark - The New Nightmare (USA) (Disc 2).chd").write_bytes(b"D2" * 64)

    # Non-ROM save file must be flagged as system artifact and excluded
    (roms_dir / "gba" / "Metroid Fusion (USA).gba").write_bytes(b"MF" * 32)
    (roms_dir / "gba" / "Metroid Fusion (USA).sav").write_bytes(b"SAV" * 32)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        grouped = client.get("/api/v1/library?grouped=true").json()
        playable = [x for x in grouped if not x["isSys"]]
        assert len(playable) == 4  # Sonic, Shinobi, Alone in the Dark, Metroid Fusion

        sonic = next(x for x in playable if "Sonic" in x["cleanTitle"])
        assert sonic["variantCount"] == 3
        assert sonic["region"] in ("World", "USA")
        assert "(Beta)" not in sonic["title"]
        assert any("(Beta)" in v["title"] for v in sonic["variants"])
        assert any("Japan" in v["region"] for v in sonic["variants"])

        shinobi = next(x for x in playable if "Shinobi" in x["title"] or "Super Shinobi" in x["title"])
        assert shinobi["variantCount"] == 2
        assert "Revenge" in shinobi["title"]

        alone = next(x for x in playable if "Alone in the Dark" in x["title"])
        assert alone["variantCount"] == 3
        assert alone["isMultiDisc"] is True
        assert alone["ext"] == "M3U"

        ungrouped = [x for x in client.get("/api/v1/library?grouped=false").json() if not x["isSys"]]
        assert len(ungrouped) == 9

        maint = client.get("/api/v1/library/maintenance").json()
        assert maint["rawPlayableCount"] == 9
        assert maint["uniqueCount"] == 4
        assert maint["collapsedDuplicates"] == 5
        assert maint["multiDiscGroups"] == 1
        assert maint["betaProtoCount"] == 1
        assert maint["systemArtifactsCount"] >= 1


def test_sagas_engine_audit_excludes_pirates_protos_and_covers_new_sagas(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    for d in ("snes", "genesis", "gba", "n64"):
        (roms_dir / d).mkdir(parents=True, exist_ok=True)

    # Problematic pirate/proto/seganet noise that must NEVER leak into Sagas
    (roms_dir / "snes" / "Pokemon Stadium (USA) (Pirate).sfc").write_bytes(b"P" * 16)
    (roms_dir / "snes" / "Metroid (World) (2023-11-30) (Beta) (Aftermarket) (Pirate).sfc").write_bytes(b"M" * 16)
    (roms_dir / "genesis" / "Ninja Gaiden (Japan) (Proto) [b].md").write_bytes(b"N" * 16)
    (roms_dir / "genesis" / "Phantasy Star II - Amia's Adventure (Japan) (SegaNet).md").write_bytes(b"PS" * 16)

    # Legitimate regional aliases + new franchise coverage
    (roms_dir / "genesis" / "Phantasy Star - Sennenki no Owari ni (Japan).md").write_bytes(b"PS4" * 16)
    (roms_dir / "genesis" / "Super Shinobi, The (Japan).md").write_bytes(b"SH1" * 16)
    (roms_dir / "gba" / "Dragon Ball Z - Supersonic Warriors (USA).gba").write_bytes(b"DBZ" * 16)
    (roms_dir / "genesis" / "Shining Force II (USA).md").write_bytes(b"SF2" * 16)
    (roms_dir / "snes" / "Teenage Mutant Ninja Turtles IV - Turtles in Time (USA).sfc").write_bytes(b"TMNT" * 16)
    (roms_dir / "snes" / "Batman Returns (USA).sfc").write_bytes(b"BAT" * 16)
    (roms_dir / "genesis" / "Castle of Illusion Starring Mickey Mouse (USA, Europe).md").write_bytes(b"MIC" * 16)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        audit = client.get("/api/v1/sagas/audit").json()
        assert audit["allClean"] is True
        assert audit["totalUnofficialLeaks"] == 0
        assert audit["chronologyErrors"] == 0
        assert audit["totalSagas"] >= 46

        sagas = client.get("/api/v1/sagas").json()
        by_id = {s["id"]: s for s in sagas}

        assert by_id["pokemon"]["ownedCount"] == 0
        assert by_id["metroid"]["ownedCount"] == 0
        assert by_id["ninja-gaiden"]["ownedCount"] == 0

        ps_owned = [e for e in by_id["phantasy-star"]["entries"] if e["owned"]]
        assert len(ps_owned) == 1
        assert ps_owned[0]["title"] == "Phantasy Star IV: The End of the Millennium"
        assert ps_owned[0]["isExtra"] is False

        sh_owned = [e for e in by_id["shinobi"]["entries"] if e["owned"]]
        assert len(sh_owned) == 1
        assert sh_owned[0]["isExtra"] is False

        for sid in ("dragon-ball", "shining", "tmnt", "batman", "disney-illusion"):
            assert by_id[sid]["ownedCount"] >= 1


def test_home_engine_endpoint_returns_deduplicated_payload(tmp_path: Path) -> None:
    roms_dir = tmp_path / "roms"
    (roms_dir / "genesis").mkdir(parents=True, exist_ok=True)
    (roms_dir / "snes").mkdir(parents=True, exist_ok=True)
    (roms_dir / "genesis" / "Streets of Rage 2 (USA).md").write_bytes(b"SOR2U" * 16)
    (roms_dir / "genesis" / "Streets of Rage 2 (Europe).md").write_bytes(b"SOR2E" * 16)
    (roms_dir / "snes" / "Chrono Trigger (USA).sfc").write_bytes(b"CT" * 16)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        home = client.get("/api/v1/home").json()
        assert home["brand"] == "Oz Game Vault"
        assert home["stats"]["uniqueRoms"] == 2
        assert home["stats"]["rawFiles"] == 3
        assert home["stats"]["collapsedDuplicates"] == 1
        assert home["hero"] is not None
        assert len(home["featured"]) == 1  # remaining canonical item outside hero
        assert home["maintenance"]["uniqueCount"] == 2



def test_smart_recommendations_kirby_attribution_and_pagination(tmp_path: Path) -> None:
    from app.recommendations import infer_item_genres

    assert "beatemup" in infer_item_genres({"cleanTitle": "Night Slashers", "consoleKey": "arcade"})
    assert "fighting" not in infer_item_genres({"cleanTitle": "Night Slashers", "consoleKey": "arcade"})
    assert "beatemup" in infer_item_genres({"cleanTitle": "Sengoku 3", "consoleKey": "arcade"})
    assert "fighting" not in infer_item_genres({"cleanTitle": "Sengoku 3", "consoleKey": "arcade"})

    roms_dir = tmp_path / "roms"
    for d in ("gba", "snes", "arcade"):
        (roms_dir / d).mkdir(parents=True, exist_ok=True)

    titles = {
        "gba": [
            "Kirby & The Amazing Mirror (USA).gba",
            "Metroid Fusion (USA).gba",
            "Castlevania - Aria of Sorrow (USA).gba",
            "Mega Man Zero (USA).gba",
            "Sonic Advance (USA).gba",
            "Yoshi's Island - Super Mario Advance 3 (USA).gba",
            "Wario Land 4 (USA).gba",
            "Kirby - Nightmare in Dream Land (USA).gba",
        ],
        "snes": [
            "Donkey Kong Country 2 (USA).sfc",
            "Super Metroid (USA).sfc",
        ],
        "arcade": [
            "kinst.zip",
            "mk3.zip",
            "nslasher.zip",
            "sengoku3.zip",
        ],
    }
    for ck, files in titles.items():
        for fname in files:
            (roms_dir / ck / fname).write_bytes(fname.encode("utf-8") * 8)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        lib = client.get("/api/v1/library").json()
        kirby = next(x for x in lib if "Kirby & The Amazing Mirror" in x["title"])
        for _ in range(5):
            assert client.post(f"/api/v1/library/{kirby['entryId']}/played").status_code == 200
        # Also test a secondary arcade play so fighting sits as a weaker secondary seed
        mk3 = next(x for x in lib if "mk3" in (x.get("relativePath") or "").lower() or "Mortal Kombat 3" in x["title"])
        assert client.post(f"/api/v1/library/{mk3['entryId']}/played").status_code == 200

        batch1 = client.get("/api/v1/recommendations?limit=6&offset=0").json()
        assert batch1["anchorTitle"].startswith("Kirby")
        for pick in batch1["fromLibrary"]:
            reason = pick.get("recReason") or ""
            title = pick.get("cleanTitle") or pick.get("title") or ""
            if "kinst" in (pick.get("relativePath") or "").lower() or "Mortal Kombat" in title:
                assert "Kirby" not in reason

        seen_ids = ",".join(p["entryId"] for p in batch1["fromLibrary"])
        batch2 = client.get(f"/api/v1/recommendations?limit=6&offset=1&exclude={seen_ids}").json()
        batch1_ids = {p["entryId"] for p in batch1["fromLibrary"]}
        batch2_ids = {p["entryId"] for p in batch2["fromLibrary"]}
        assert batch1_ids.isdisjoint(batch2_ids)


def test_mood_picks_full_library_pagination_and_home_order_migration(tmp_path: Path) -> None:
    db_path = tmp_path / "data" / "oz_hub.db"
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(db_path))
    conn.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    conn.execute(
        "INSERT INTO settings (key, value) VALUES (?, ?)",
        ("home_section_order", json.dumps(["featured", "recommendations", "consoles", "recent", "favorites"])),
    )
    conn.commit()
    conn.close()

    roms_dir = tmp_path / "roms"
    for d in ("snes", "genesis", "gba"):
        (roms_dir / d).mkdir(parents=True, exist_ok=True)
    for i in range(32):
        ck = ("snes", "genesis", "gba")[i % 3]
        ext = {"snes": "sfc", "genesis": "md", "gba": "gba"}[ck]
        (roms_dir / ck / f"Retro Game {i:02d} (USA).{ext}").write_bytes(f"ROM{i}".encode("utf-8") * 12)

    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        settings = client.get("/api/v1/settings").json()
        assert settings["home_section_order"] == ["consoles", "recommendations", "featured", "recent", "favorites"]

        seen: list[str] = []
        # 10 consecutive rolls * 3 = 30 unique games (> previous 24 cap) without repeating
        for step in range(10):
            resp = client.get(
                "/api/v1/recommendations/mood",
                params={"mood": "surprise", "offset": step, "exclude": ",".join(seen)},
            )
            assert resp.status_code == 200
            payload = resp.json()
            assert payload["totalMatches"] == 32
            assert len(payload["picks"]) == 3
            assert payload["cycled"] is False
            for p in payload["picks"]:
                assert p["entryId"] not in seen
                seen.append(p["entryId"])
        assert len(seen) == 30


def test_ia_extended_platform_filtering_and_detection() -> None:
    from app.ia_client import _infer_extended_platform, _is_clean_ia_rom_doc

    assert _is_clean_ia_rom_doc("New Super Mario Bros. (USA)", "nsmb_nds_rom", "nds") is True
    assert _is_clean_ia_rom_doc("Nintendo DS Cartridge Holder 3D Print", "thingiverse-1234", "nds") is False
    assert _is_clean_ia_rom_doc("Sonic Adventure Original Soundtrack OST", "sa_ost", "dc") is False
    assert _is_clean_ia_rom_doc("Sonic Adventure (USA) (for Xbox 360)", "sa_xbox", "dc") is False

    assert _infer_extended_platform("Mario Kart DS (USA)", "mario_kart_nds", "Nintendo DS") == "nds"
    assert _infer_extended_platform("God of War - Chains of Olympus", "gow_psp", "PlayStation Portable") == "psp"
    assert _infer_extended_platform("Sonic Adventure 2 (USA)", "sa2_dc", "Dreamcast GDI") == "dc"
    assert _infer_extended_platform("The Legend of Zelda - The Wind Waker", "ww_gc", "Nintendo GameCube RVZ") == "gc"
    assert _infer_extended_platform("Super Mario Galaxy (USA)", "smg_wbfs", "Nintendo Wii") == "wii"


def test_discover_catalog_rotation_and_sagas_ai_audit(tmp_path: Path) -> None:
    from app.recommendations import compute_local_recommendations

    library = [
        {"entryId": "g1", "cleanTitle": "Super Mario World", "consoleKey": "snes", "playCount": 3, "favorite": True},
        {"entryId": "g2", "cleanTitle": "Street Fighter II", "consoleKey": "arcade", "playCount": 2, "favorite": False},
    ]
    rec_a = compute_local_recommendations(library, limit=4, seed=1, discover_limit=8)
    rec_b = compute_local_recommendations(library, limit=4, seed=9, discover_limit=8)
    disc_a = [x["title"] for x in rec_a["discoverCatalog"]]
    disc_b = [x["title"] for x in rec_b["discoverCatalog"]]
    assert len(disc_a) == 8
    assert len(disc_b) == 8
    assert disc_a != disc_b

    roms_dir = tmp_path / "roms"
    (roms_dir / "snes").mkdir(parents=True, exist_ok=True)
    (roms_dir / "snes" / "Super Mario World (USA).sfc").write_bytes(b"SMW" * 16)
    app = create_app(data_dir=tmp_path / "data", roms_dir_override=str(roms_dir))
    with TestClient(app) as client:
        resp = client.post("/api/v1/sagas/super-mario/ai-audit")
        assert resp.status_code == 200
        audit = resp.json()
        assert audit["sagaId"] == "super-mario"
        assert 45 <= audit["score"] <= 100
        assert isinstance(audit["chronologyOk"], bool)
        assert audit["source"] == "local"
        assert isinstance(audit["summary"], str) and len(audit["summary"]) > 10

