# RELEASE_PROVENANCE_PROTOCOL

## Purpose
Make every Oz APK/AAB reconstructable and attributable to exact source, toolchain, validation and signing evidence.

## Provenance chain
```text
SOURCE SHA → DEPENDENCY STATE → TOOLCHAIN → BUILD → TESTS → APK/AAB → SIGNING IDENTITY → SHA-256 → SBOM → PROVENANCE/ATTESTATION → RELEASE NOTES → DEVICE VALIDATION
```

## Release manifest must answer
- What exact source SHA built this artifact?
- What branch/tag and dependency/toolchain versions were used?
- Which tests/gates passed, failed or were NOT RUN?
- Which device validated it and when?
- What artifact filename and SHA-256 identify it?
- How was it signed (identity/fingerprint, never private key material)?
- Is SBOM/attestation available?
- What known issues remain?

## Rules
- Never call an APK “latest” without provenance.
- Build output from a dirty/unidentified tree is not a publishable release artifact.
- Signing secrets/private keys never enter documentation or repository.
- A release may be blocked even with Build PASS if Interaction/Device/Security evidence is missing for the change class.
