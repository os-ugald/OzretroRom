#!/usr/bin/env python3
"""Static security/compatibility contracts for the Ready Core 01B IA login hotfix."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"
TESTS = ROOT / "app/src/test/java/cl/gakuto/kizuna/auth"

checks = 0
failures: list[str] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(path: Path, *tokens: str) -> None:
    global checks
    source = read(path)
    for token in tokens:
        checks += 1
        if token not in source:
            failures.append(f"{path.relative_to(ROOT)}: falta {token!r}")


def forbid(path: Path, *tokens: str) -> None:
    global checks
    source = read(path)
    for token in tokens:
        checks += 1
        if token in source:
            failures.append(f"{path.relative_to(ROOT)}: contiene {token!r}")


activity = JAVA / "ui/InternetArchiveLoginActivity.kt"
auth = JAVA / "auth/InternetArchiveAuthManager.kt"
parser = JAVA / "auth/InternetArchiveS3PageParser.kt"
policy = JAVA / "auth/InternetArchiveWebLoginPolicy.kt"
parser_test = TESTS / "InternetArchiveS3PageParserTest.kt"
policy_test = TESTS / "InternetArchiveWebLoginPolicyTest.kt"

# Capture is tied to the official top-level page and has no persistent bridge.
require(activity, "isS3KeysPageUrl", "evaluateJavascript", "DOM_EXTRACTION_SCRIPT", "view.url.orEmpty()")
require(policy, 'ARCHIVE_HOST = "archive.org"', 'S3_KEYS_PATH = "/account/s3.php"', "isTrustedDownloadUrl", "uri.userInfo == null", "uri.port == 443")
forbid(activity, "addJavascriptInterface", "onReceivedSslError", "proceed()")

# Existing keys are read conservatively; no endpoint or control can generate/rotate/destroy them.
require(parser, "hasOfficialLabels", "unique.length === 2", "input.getClientRects().length > 0", "^[A-Za-z0-9]{16}$")
forbid(parser, "XMLHttpRequest", "fetch(", ".click()", "Destroy Old Keys", "Generate New Keys")
forbid(activity, "Destroy Old Keys", "Generate New Keys")

# DOM, authenticated HTML and manual input all converge on validation before encrypted storage.
require(auth, "completeLoginFromCookies", "completeLoginFromPageKeys", "importS3Credentials", "verifyAndSave", "probeCredentials")
require(auth, 'listOf("HEAD", "GET")', "saveVerifiedSession", "AndroidKeyStore", "AES/GCM/NoPadding")
require(activity, "PasswordVisualTransformation", "Usar claves", "No compartas capturas", "completeLoginFromPageKeys")

# Preference/key aliases remain unchanged so an app update preserves already-working sessions.
require(auth, 'PREFS = "oz_internet_archive_auth"', 'KEY_SESSION = "session.v1"', 'KEY_ALIAS = "oz_retrorom_ia_session_v1"')
require(auth, 'put("schema", 2)', "Decodes both Fix5 schema 1 and Auth V2 schema 2")

# JVM regression coverage includes current/legacy IA markup, ambiguity and origin/path attacks.
require(parser_test, "readsCurrentPageWhenInputsHaveNoNameOrId", "keepsLegacyNameAndIdFormatsRegardlessOfAttributeOrder")
require(parser_test, "rejectsUnrelatedOrAmbiguousSixteenCharacterValues", "parsesQuotedWebViewResultWithoutLoggingOrJsonDependencies")
require(policy_test, "allowsOnlyHttpsArchiveHostsOnTheStandardPort", "credentialExtractionRequiresTheExactOfficialOriginAndPath")
require(policy_test, "archive.org.evil.example", "evil.example@archive.org", "archive.org:8443")

if failures:
    print(f"READY_CORE_01B_IA_LOGIN_FAILED checks={checks} failures={len(failures)}")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)

print(f"READY_CORE_01B_IA_LOGIN_OK checks={checks}")
