# Oz Source Classification Foundation

## Permanent rule

Oz preserves relevant regional, language, translation, homebrew, aftermarket, prototype, beta, demo, modification, and provider-classification evidence for every platform. Presentation may filter evidence; ingestion must not discard it merely to simplify the catalog.

Provider metadata is evidence. Oz owns normalized release classification. Provider collection labels never become canonical truth automatically.

## Provider-neutral release facets

`ReleaseFacets` keeps independent dimensions:

- `regions[]`: evidenced release regions;
- `languages[]`: evidenced playable languages;
- `publicationClass`: `RETAIL`, `AFTERMARKET`, `HOMEBREW`, `PROTOTYPE`, `BETA`, `DEMO`, `UNRELEASED`, or `UNKNOWN`;
- `modificationClass`: `ORIGINAL`, `TRANSLATION`, `ROM_HACK`, `MODIFIED`, or `UNKNOWN`;
- `translation`: independently records source language, target languages, and evidence status;
- `regionalExclusivity`: `CONFIRMED_EXCLUSIVE`, `NOT_EXCLUSIVE`, or `UNKNOWN`, independently from region;
- `classificationEvidence[]` and `evidenceStatus`: preserve why a normalized value exists.

Publication and modification are orthogonal: `RETAIL + TRANSLATION`, `HOMEBREW + ORIGINAL`, `AFTERMARKET + ORIGINAL`, and `PROTOTYPE + TRANSLATION` are valid combinations. Aftermarket and homebrew are not synonyms.

`(Japan)` in a provider filename is region evidence only. It never proves exclusivity. USA/Europe/Japan never imply playable language without authoritative or curated evidence. T-En membership is translation provenance, not canonical language truth by itself.

Provider classifications such as No-Intro, T-En Collection, RetroAchievements, Aftermarket, Satellaview, and Sufami Turbo stay attached as provenance. Normalization can later derive queryable facets such as `platform=SNES + region=JAPAN + translationTarget=ENGLISH` without using provider labels as identity.

## Identity boundary

`Logical Game != Release != SourceCandidate`. Stable ReleaseId remains `GameId + canonicalReleaseKey`. Facets, provider labels, No-Intro/T-En/RetroAchievements membership, providerItemId, and trackers are excluded from stable GameId/ReleaseId/CandidateId. Facet enrichment cannot churn identity. A proven provisional-identity error requires an explicit reconciliation/migration.

## Hash evidence contract

- `UNKNOWN` with no digest is valid.
- `DISTRIBUTED_ARTIFACT` requires at least one syntactically valid artifact digest.
- `ROM_CONTENT` requires at least one syntactically valid content digest.
- Digests require explicit scope and exact hexadecimal length: CRC32 8, MD5 32, SHA1 40, SHA256 64.
- Missing claimed evidence, malformed values, contradictory canonical evidence, or contradictory `CONTENT_IDENTICAL` artifact/content evidence fail closed.
- Hash enrichment is evidence evolution and cannot change stable ReleaseId.
