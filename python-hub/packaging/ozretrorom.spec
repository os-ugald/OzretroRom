# -*- mode: python ; coding: utf-8 -*-

from pathlib import Path

hub_root = Path(SPECPATH).resolve().parent

a = Analysis(
    [str(hub_root / "app" / "desktop.py")],
    pathex=[str(hub_root)],
    binaries=[],
    datas=[
        (str(hub_root / "app" / "static" / "ui.html"), "app/static"),
        (str(hub_root / "app" / "static" / "styles.css"), "app/static"),
        (str(hub_root / "app" / "static" / "js"), "app/static/js"),
        (str(hub_root / "app" / "static" / "assets"), "app/static/assets"),
        (str(hub_root / "app" / "static" / "fonts"), "app/static/fonts"),
        (str(hub_root / "app" / "data"), "app/data"),
        (str(hub_root / "app" / "source-index" / "*.json.gz.bin"), "app/source-index"),
    ],
    hiddenimports=[
        "app.main",
        "uvicorn.logging",
        "uvicorn.loops",
        "uvicorn.loops.auto",
        "uvicorn.protocols",
        "uvicorn.protocols.http",
        "uvicorn.protocols.http.auto",
        "uvicorn.protocols.websockets",
        "uvicorn.protocols.websockets.auto",
        "uvicorn.lifespan",
        "uvicorn.lifespan.on",
        "anyio",
        "httpx",
        "PIL",
        "webview",
        "webview.platforms.edgechromium",
        "bottle",
        "clr_loader",
        "pythonnet",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="OzRetroRom",
    icon=str(hub_root / "packaging" / "ozretrorom.ico"),
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="OzRetroRom",
)
