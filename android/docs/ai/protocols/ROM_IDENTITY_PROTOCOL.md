# ROM_IDENTITY_PROTOCOL

## Scope
Applies across catalog, download, library, collections, launch and migration.

## Conceptual identity key
`platform + serial/hash when available + canonical game identity + release/edition + region + revision + disc + title provenance`

## Match authority
```text
strong identity (hash/serial/DAT)
> explicit verified metadata
> verified alias
> fuzzy title
```

Fuzzy matching is fallback, never authority.

## Mandatory rules
- sequel mismatch → REJECT
- disc mismatch → REJECT unless the exact set/disc was explicitly selected
- edition/revision mismatch → never silently merge
- region preference → rank candidates, never rewrite identity
- alternate title → requires provenance
- canonical game ≠ release artifact
- normalized title is insufficient as sole identity

## Minimum regression fixtures
- X-Men: Mutant Academy ≠ X-Men: Mutant Academy 2
- Resident Evil ≠ Resident Evil 2
- Mega Man X ≠ Mega Man X2
- Street Fighter II ≠ Street Fighter III
- Final Fantasy VII ≠ Final Fantasy VIII

Roman numerals and Arabic numbers may be compared semantically only with title-aware parsing; ordinary letters must never be converted into sequel numbers.

## Evidence
Record platform, identity evidence, source, region/release/disc markers, proposed mapping, confidence and contradictions. Every real identity bug should become a regression fixture.
