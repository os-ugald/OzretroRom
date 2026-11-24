# Oz Source Index v1

`builder.py` is the CI-side normalization boundary. Provider adapters consume unstable upstream contracts and emit deterministic, provider-neutral records. Android consumes only Oz-controlled platform shards.

## Format decision

| Format | Size/compression | Android lookup | Evolution | Determinism/debugging | Decision |
|---|---|---|---|---|---|
| SQLite | Good | Excellent indexed lookup | migrations required | binary and harder to diff | Later option for large shards |
| JSONL + gzip | Excellent streaming | requires scan/index | additive records | excellent | Viable for very large streaming builders |
| JSON + gzip | Excellent for platform shards | in-memory exact lookup | additive fields/version gate | canonical and inspectable | **v1 prototype** |
| CBOR/protobuf | Excellent | fast | schema tooling | less transparent/new dependencies | Deferred |

The v1 prototype uses canonical JSON compressed with deterministic gzip. **This is a PROVISIONAL FORMAT PENDING PRODUCTION-SCALE SNES BENCHMARK.** Sharding bounds memory and transfer size. If production SNES scale proves full parsing too expensive, the same normalized schema can be emitted as SQLite without changing canonical or provider identities.

## Identity and hash scope

`GameId` v1 is explicitly a `PROVISIONAL_TITLE_KEY`, built from platform plus a normalized, curated Oz title key. Display-title corrections and aliases do not change it, but it is not claimed as globally immutable; a later canonical registry can alias this provisional ID without treating spelling as content identity.

An unresolved release is explicitly `PROVISIONAL_METADATA_KEY`. Stable ReleaseId always uses `GameId + canonicalReleaseKey`, where the latter is a curated, provider-neutral Oz key rather than filename/providerItemId/artifact hash. Two curated keys cannot collapse. Real `ROM_CONTENT` evidence upgrades status to `VERIFIED_CONTENT` without changing ReleaseId; CRC32/SHA1/SHA256 enrichment remains separate evidence. Cryptographic deduplication/reconciliation may derive transient fingerprints from that evidence, but evolving fingerprints are never persistent Release IDs.

MiNERVA's verified ZIP hashes are stored on the candidate artifact as `DISTRIBUTED_ARTIFACT`; they do not define canonical release identity. `UNKNOWN` hash scope never authorizes an exact match. Artifact/content relation is separately `WRAPPED`, `CONTENT_IDENTICAL`, or `UNKNOWN`: current MiNERVA ZIP is WRAPPED, while a validated raw-ROM provider may mark CONTENT_IDENTICAL and contribute the same bytes as legitimate ROM_CONTENT evidence.

`CandidateId` uses provider + transport + immutable torrent infohash/file index/exact path. Provider item IDs, collection labels, `.torrent` artifact strategy/path and tracker URLs remain provenance or mutable transport hints and cannot churn CandidateId. The Android adapter reconstructs a magnet with stored tracker hints when present while retaining the verified torrent-artifact locator for a future resolver.

## Promotion and integrity

CI builds into `staging/` with health `NOT_EVALUATED`, verifies schema and shard SHA-256, compares health metrics against `current/`, then either records ACCEPT and atomically promotes `staging → current` while retaining `current → previous`, or records QUARANTINE/REJECT and leaves `current` untouched. Thresholds default to hard reject for a missing/zero critical platform, quarantine above a 20% release drop, and quarantine above a 15 percentage-point coverage collapse; CI configuration may tighten them with reviewed evidence.

The manifest reserves Ed25519 `keyId` and signature fields. Production signing uses a standard CI secret-backed private key; Android embeds only trusted public keys. No private key belongs in Git. BitTorrent candidates must later carry a user-facing disclosure that swarm participation exposes the user's public IP to peers.

## Production-scale SNES host benchmark

The metadata-only benchmark consumes verified `.torrent` metadata and never retrieves payload bytes:

```powershell
python -m unittest -v test_snes_benchmark.py
python snes_benchmark.py --spec ../../.artifacts/source-index-snes-benchmark/input/benchmark-spec.json --output ../../.artifacts/source-index-snes-benchmark/output
```

Its controlled scope is the primary No-Intro SNES collection plus Aftermarket. Specialty, private, translation, RetroAchievements, Satellaview, Sufami Turbo, and source-code collections are intentionally excluded until their identity semantics are separately normalized.

`snes_benchmark.py` is a benchmark metadata normalizer, not the production canonical game-knowledge engine. Its filename-derived region/publication values remain `PROVIDER_DERIVED`; language and regional exclusivity remain `UNKNOWN` unless separate evidence proves them. Provider collection classifications are retained without entering GameId, ReleaseId, or CandidateId.

The same parameterized metadata benchmark is also used for controlled cartridge
systems such as NES and Genesis. `genesis_census.py` separately classifies every
Genesis-adjacent dashboard collection so deferred translations/specialty media
and the separate 32X system cannot enter the Genesis v1 shard silently.

GBA uses the same `snes_benchmark.py -> builder.py` path. `gba_census.py` is only
a metadata classifier: it inventories all GBA-adjacent torrent metadata, records
exact filename overlaps and keeps translations, specialty media, private sets
and RetroAchievements noise outside the controlled base + Aftermarket shard.
Benchmark diagnostics now also emit explicit-language/UNKNOWN counts,
`overlap-warnings.json`, and `collision-risk.json`; these reports never merge or
rewrite Source Index identity.
