"""Generate PC Hub and Android brand icons and copy splash assets from shared/."""
from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image, ImageDraw

HYLIAN_BG_RGB = (10, 15, 13)
HYLIAN_BG_RGBA = (10, 15, 13, 255)
ICO_SIZES = [(16, 16), (32, 32), (48, 48), (64, 64), (256, 256)]
ANDROID_DENSITIES: dict[str, tuple[int, int]] = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}


def find_logo_master(shared_dir: Path) -> Path:
    for ext in (".png", ".jpg", ".jpeg", ".webp"):
        candidate = shared_dir / f"logo_master{ext}"
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f"No logo_master.* found in {shared_dir}")


def load_square_logo(logo_path: Path) -> Image.Image:
    with Image.open(logo_path) as img:
        rgba = img.convert("RGBA")
        w, h = rgba.size
        if w != h:
            side = min(w, h)
            left = (w - side) // 2
            top = (h - side) // 2
            rgba = rgba.crop((left, top, left + side, top + side))
        return rgba.copy()


def _circle_mask(size: int) -> Image.Image:
    scale = 4
    mask_hi = Image.new("L", (size * scale, size * scale), 0)
    draw = ImageDraw.Draw(mask_hi)
    draw.ellipse((0, 0, size * scale - 1, size * scale - 1), fill=255)
    return mask_hi.resize((size, size), Image.Resampling.LANCZOS)


def _render_square_on_hylian(master: Image.Image, size: int) -> Image.Image:
    fg = master.resize((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), HYLIAN_BG_RGBA)
    return Image.alpha_composite(canvas, fg)


def _render_round_icon(master: Image.Image, size: int) -> Image.Image:
    base = _render_square_on_hylian(master, size)
    mask = _circle_mask(size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def _render_adaptive_foreground(master: Image.Image, canvas_size: int) -> Image.Image:
    # 66dp safe zone inside 108dp adaptive icon grid
    inner = max(1, round(canvas_size * (66.0 / 108.0)))
    fg = master.resize((inner, inner), Image.Resampling.LANCZOS)
    mask = _circle_mask(inner)
    offset = (canvas_size - inner) // 2
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    canvas.paste(fg, (offset, offset), mask)
    return canvas


def main() -> None:
    repo_root = Path(__file__).resolve().parents[2]
    shared_dir = repo_root / "shared"
    logo_src = find_logo_master(shared_dir)
    splash_src = shared_dir / "splash_hylian.gif"
    if not splash_src.is_file():
        raise FileNotFoundError(f"Missing splash gif: {splash_src}")

    master = load_square_logo(logo_src)
    generated: list[tuple[Path, str]] = []

    # 1. PC Hub static & PyInstaller packaging assets
    static_assets = repo_root / "python-hub" / "app" / "static" / "assets"
    static_assets.mkdir(parents=True, exist_ok=True)
    packaging_dir = repo_root / "python-hub" / "packaging"
    packaging_dir.mkdir(parents=True, exist_ok=True)

    logo_png = static_assets / "logo.png"
    _render_square_on_hylian(master, 512).save(logo_png, format="PNG", optimize=True)
    generated.append((logo_png, "512x512"))

    favicon_ico = static_assets / "favicon.ico"
    master_256 = _render_square_on_hylian(master, 256)
    master_256.save(favicon_ico, format="ICO", sizes=ICO_SIZES)
    generated.append((favicon_ico, "multi-res 16/32/48/64/256"))

    packaging_ico = packaging_dir / "ozretrorom.ico"
    master_256.save(packaging_ico, format="ICO", sizes=ICO_SIZES)
    generated.append((packaging_ico, "multi-res 16/32/48/64/256"))

    hub_splash = static_assets / "splash_hylian.gif"
    shutil.copy2(splash_src, hub_splash)
    generated.append((hub_splash, "640x360 GIF"))

    # 2. Android mipmaps + res/raw splash
    res_dir = repo_root / "android" / "app" / "src" / "main" / "res"
    for density, (launcher_px, adaptive_px) in ANDROID_DENSITIES.items():
        mip_dir = res_dir / f"mipmap-{density}"
        mip_dir.mkdir(parents=True, exist_ok=True)

        ic_launcher = mip_dir / "ic_launcher.png"
        _render_square_on_hylian(master, launcher_px).save(ic_launcher, format="PNG", optimize=True)
        generated.append((ic_launcher, f"{launcher_px}x{launcher_px}"))

        ic_round = mip_dir / "ic_launcher_round.png"
        _render_round_icon(master, launcher_px).save(ic_round, format="PNG", optimize=True)
        generated.append((ic_round, f"{launcher_px}x{launcher_px} round"))

        ic_fg = mip_dir / "ic_launcher_foreground.png"
        _render_adaptive_foreground(master, adaptive_px).save(ic_fg, format="PNG", optimize=True)
        generated.append((ic_fg, f"{adaptive_px}x{adaptive_px} adaptive fg"))

    raw_dir = res_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    android_splash = raw_dir / "splash_hylian.gif"
    shutil.copy2(splash_src, android_splash)
    generated.append((android_splash, "640x360 GIF"))

    print(f"Source logo: {logo_src.relative_to(repo_root)} ({master.size[0]}x{master.size[1]})")
    print(f"Source splash: {splash_src.relative_to(repo_root)}")
    for out_path, spec in generated:
        rel = out_path.relative_to(repo_root)
        print(f"  [OK] {rel} | {spec} | {out_path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
