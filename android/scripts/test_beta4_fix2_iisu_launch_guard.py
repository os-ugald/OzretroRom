from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SESSION = ROOT / "app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt"
VALIDATOR = ROOT / "app/src/main/java/cl/gakuto/kizuna/launch/RomPreparedFileValidator.kt"
BUILD = ROOT / "app/build.gradle.kts"

def need(path, *tokens):
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        assert token in text, f"{path.name}: falta {token!r}"
    return text

session = need(
    SESSION,
    "IISU_STALE_DESCRIPTOR_SOURCE_CLEARED",
    "isOzVirtualDescriptorReference",
    "downloaded?.asSource()",
    "preferences.setAuthorizedSource(loaded.id, null)",
    "verifyRetroFileAccess(current, retroEntry)",
)
need(
    VALIDATOR,
    "isOzVirtualDescriptor",
    "oz-retrorom.json",
    "oz-virtual-on-demand",
    "iiSU entregó un descriptor virtual de Oz",
)
need(BUILD, "versionCode = 60010", 'versionName = "0.6.0-beta.4"')
assert session.find("downloaded?.asSource()") < session.find("?: remembered"), "La ROM persistente debe tener prioridad"
print("BETA4_FIX2_IISU_LAUNCH_GUARD_OK checks=11")
