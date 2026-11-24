# Oz Continuous Role Qualification

Canonical protocol for keeping specialist competence, professional alignment,
technology research and Oz assurance separate. It governs evidence, not product
or implementation authority.

## Qualification states

- `BASELINE_ROLE`: stable internal role contract exists.
- `STANDARD_ALIGNED`: current standards/body-of-knowledge mapping is recorded.
- `OZ_QUALIFIED`: relevant Oz evidence plus independent reviewer/Forge assurance.
- `EXTERNALLY_CERTIFIED`: only a real, verifiable credential held by a valid
  person or entity; never attribute a human certification to an AI.

Alignment or qualification never adds authority; Workspace, Forge, Product
Owner and Gate rules remain controlling.

## Evidence layers

1. **ROLE CONTRACT** — stable mission, competencies, scope and failure modes.
2. **PROFESSIONAL ALIGNMENT** — standards, Bodies of Knowledge and competency
   frameworks; no framework is mandatory when it does not fit the domain.
3. **TECHNOLOGY / RESEARCH REFRESH** — changing APIs, frameworks, upstreams,
   release notes, papers and compatibility facts.
4. **OZ QUALIFICATION** — internal benchmarks/evidence plus independent assurance.

Role files must not freeze volatile technology facts. Unknowns and gaps stay
explicit. Emulator integration may have no universal professional certification;
use primary specifications/upstreams and declare that limitation.

## Source hierarchy

1. Standards body or official specification.
2. Official vendor/project documentation.
3. Official upstream repository or release notes.
4. Peer-reviewed research/original academic publication when applicable.
5. Secondary sources only for discovery/context, never as authority when a
   primary source exists.

## Credential alignment

```text
[CREDENTIAL ALIGNMENT]
ROLE:
OFFICIAL BODY / STANDARD:
VERSION / DATE:
SCOPE USED:
PRIMARY SOURCES:
KNOWN GAPS:
STATUS:
```

## Technology refresh

```text
[TECHNOLOGY REFRESH]
ROLE:
TOPIC:
AS_OF:
PRIMARY SOURCES:
CURRENT_FINDINGS:
CHANGES_RELEVANT_TO_OZ:
KNOWN_GAPS:
REFRESH_TRIGGER:
```

Refresh when a decision depends on current version/API/compatibility; evidence
is materially stale; a platform or upstream changes; historical/current docs
contradict; an R2/R3 Gate makes technology material; or Forge/Product Owner
requests current research. Do not search when stable primary evidence suffices.

The implementer may produce evidence but cannot self-certify R2/R3 closure.
