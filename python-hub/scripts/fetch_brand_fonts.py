"""Fetch Oxanium (600, 700) and Space Grotesk (400, 500, 600, 700) for offline Android & PC Hub."""
from __future__ import annotations

import re
from pathlib import Path

import httpx

CSS_URL = (
    "https://fonts.googleapis.com/css2"
    "?family=Oxanium:wght@600;700"
    "&family=Space+Grotesk:wght@400;500;600;700"
    "&display=swap"
)

TTF_UA = "curl/7.81.0"
WOFF2_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)

FONT_SPECS = {
    ("Oxanium", "600"): ("oxanium_semibold.ttf", "Oxanium-SemiBold.woff2", "Oxanium-SemiBold.ttf"),
    ("Oxanium", "700"): ("oxanium_bold.ttf", "Oxanium-Bold.woff2", "Oxanium-Bold.ttf"),
    ("Space Grotesk", "400"): ("space_grotesk_regular.ttf", "SpaceGrotesk-Regular.woff2", "SpaceGrotesk-Regular.ttf"),
    ("Space Grotesk", "500"): ("space_grotesk_medium.ttf", "SpaceGrotesk-Medium.woff2", "SpaceGrotesk-Medium.ttf"),
    ("Space Grotesk", "600"): ("space_grotesk_semibold.ttf", "SpaceGrotesk-SemiBold.woff2", "SpaceGrotesk-SemiBold.ttf"),
    ("Space Grotesk", "700"): ("space_grotesk_bold.ttf", "SpaceGrotesk-Bold.woff2", "SpaceGrotesk-Bold.ttf"),
}


def _parse_font_faces(css: str) -> dict[tuple[str, str], str]:
    blocks = re.findall(r"(?:/\*\s*([\w-]+)\s*\*/\s*)?@font-face\s*\{([^}]+)\}", css)
    out: dict[tuple[str, str], str] = {}
    for subset, body in blocks:
        fam_m = re.search(r"font-family:\s*'([^']+)'", body)
        wt_m = re.search(r"font-weight:\s*(\d+)", body)
        url_m = re.search(r"src:\s*url\(([^)]+)\)", body)
        if not (fam_m and wt_m and url_m):
            continue
        key = (fam_m.group(1), wt_m.group(1))
        # Prefer standard 'latin' subset when multiple subsets are present
        if subset == "latin" or key not in out:
            out[key] = url_m.group(1)
    return out


def main() -> None:
    repo_root = Path(__file__).resolve().parents[2]
    android_font_dir = repo_root / "android" / "app" / "src" / "main" / "res" / "font"
    web_font_dir = repo_root / "python-hub" / "app" / "static" / "fonts"
    android_font_dir.mkdir(parents=True, exist_ok=True)
    web_font_dir.mkdir(parents=True, exist_ok=True)

    with httpx.Client(follow_redirects=True, timeout=20.0) as client:
        ttf_css = client.get(CSS_URL, headers={"User-Agent": TTF_UA}).text
        woff2_css = client.get(CSS_URL, headers={"User-Agent": WOFF2_UA}).text

        ttf_urls = _parse_font_faces(ttf_css)
        woff2_urls = _parse_font_faces(woff2_css)

        for key, (android_name, web_woff2_name, web_ttf_name) in FONT_SPECS.items():
            if key not in ttf_urls or key not in woff2_urls:
                raise RuntimeError(f"Missing font URL for {key}")

            ttf_bytes = client.get(ttf_urls[key]).raise_for_status().content
            woff2_bytes = client.get(woff2_urls[key]).raise_for_status().content

            (android_font_dir / android_name).write_bytes(ttf_bytes)
            (web_font_dir / web_ttf_name).write_bytes(ttf_bytes)
            (web_font_dir / web_woff2_name).write_bytes(woff2_bytes)
            print(f"[OK] {key}: android/{android_name} ({len(ttf_bytes)} B), web/{web_woff2_name} ({len(woff2_bytes)} B)")


if __name__ == "__main__":
    main()
