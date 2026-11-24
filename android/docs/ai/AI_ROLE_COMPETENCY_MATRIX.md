# Oz RetroRom — AI Role Competency & Standards Matrix

**VERSION:** 1.0
**REVIEW_DATE:** 2026-08-29
**PURPOSE:** strengthen Dream Team roles using current professional bodies of
knowledge, standards and certification competency outlines without making false
credential claims.

---

## 1. Critical rule: alignment is not certification

Oz AI roles are **internal role contracts**. They are not currently certified by
PMI, Scrum.org, The Open Group, ISTQB, ISC2, Google, OWASP or another external body.

An AI role generally cannot truthfully claim a human credential that requires
identity, eligibility, work experience, training, a proctored assessment or a
verifiable certificate.

Therefore:

- never write `PMP-certified AI`, `TOGAF-certified architect`,
  `ISTQB-certified QA`, etc. without a real verifiable holder;
- never use leaked exam questions or impersonate a credential holder;
- use official public syllabus/BoK/exam domains as **competency frameworks**;
- record sources, date/version, scope and gaps;
- qualify internally through evidence and independent review.

---

## 2. Oz qualification states

### `BASELINE_ROLE`

Internal role charter exists; no current external-alignment review is claimed.

### `STANDARD_ALIGNED`

Current official standards/credential competency domains were researched and
mapped to the role for the current scope.

### `OZ_QUALIFIED`

The role produced evidence on an Oz-relevant benchmark/scenario and a separate
reviewer/Forge assurance accepted the result.

### `EXTERNALLY_CERTIFIED`

Reserved for an actual verifiable external credential held by a real human or
legal entity where that credential applies. Never assigned automatically to an AI.

---

## 3. Continuous Qualification Protocol

Canonical operating protocol: [`CONTINUOUS_ROLE_QUALIFICATION.md`](CONTINUOUS_ROLE_QUALIFICATION.md).
This matrix summarizes alignment and does not create a parallel authority.

The canonical operational protocol is
[`CONTINUOUS_ROLE_QUALIFICATION.md`](CONTINUOUS_ROLE_QUALIFICATION.md). This
matrix summarizes alignment; it does not create a second qualification
authority.

When Forge activates a specialist for material R2/R3 work, the role may invoke
**Credential & Standards Refresh**:

1. identify the most relevant official professional body/standard;
2. verify that the credential/standard is current;
3. read current official competency domains/Body of Knowledge;
4. map those domains to the Oz task;
5. use official/primary technical docs for implementation-specific facts;
6. declare gaps where certification content does not cover the problem;
7. run an Oz-specific benchmark/review;
8. obtain independent assurance;
9. record the alignment in Markdown when materially reusable.

Refresh is required when:
- the relevant standard/credential materially changes;
- a previous source is retired/deprecated;
- the role is about to make an R2/R3 recommendation using stale evidence;
- Forge or the Product Owner requests requalification.

Technology refresh is distinct from professional alignment: changing APIs,
frameworks, upstreams, release notes and compatibility facts belong in a
`[TECHNOLOGY REFRESH]` record with an explicit `AS_OF` date and primary sources.

---

## 4. Role matrix

### Product / Technical Lead

**Professional alignment**
- PMI Project Management Professional (PMP) competency domains
- PMI Agile Certified Practitioner (PMI-ACP)
- Scrum.org Professional Scrum Product Owner (PSPO)

**Use in Oz**
- value/outcome orientation
- stakeholder/Product Owner accountability
- prioritization
- risk and delivery governance
- product vision/backlog/release reasoning

**Important**
Oz is not required to implement Scrum mechanically. These frameworks improve
professional reasoning; they do not override Oz governance.

### Software Architect

**Professional alignment**
- The Open Group TOGAF Enterprise Architecture Foundation/Practitioner

**Use in Oz**
- architecture views/boundaries
- stakeholder and requirements reasoning
- transition/change governance
- gap analysis
- cross-system contracts

**Important**
TOGAF is a competence reference, not a mandate to add enterprise bureaucracy.

### Android Engineer

**Professional alignment**
- Android Developers official documentation and architecture guidance
- Jetpack/AndroidX official guidance
- OWASP MASVS/MASTG for mobile-security relevant work

**Certification status**
Google's Associate Android Developer exam is retired and must not be used as a
current certification target.

**Oz qualification**
Code/build/test/device evidence is more meaningful than pretending a retired
credential exists.

### UI/UX Product Designer

**Professional alignment**
- platform accessibility/usability standards as applicable
- Android accessibility guidance
- W3C accessibility standards where applicable
- Oz approved visual specification

**Oz qualification**
Visual Parity, interaction evidence, controller/touch behavior and usability
outcomes are the primary qualification evidence.

### Download / Network Engineer

**Professional alignment**
- IETF protocol standards/RFCs relevant to the transport
- Android networking/background-execution official docs
- upstream HTTP/BitTorrent/library documentation

**Oz qualification**
Resume correctness, recovery, network handoff, telemetry and real-device evidence.

### Emulation Integration Engineer

**Professional alignment**
There is no recognized universal professional certification for emulator/core
integration.

**Primary authority**
- official emulator/core repositories
- Libretro/RetroArch official docs
- platform/media/BIOS evidence
- physical device and Golden Game Suite evidence

**Oz qualification**
`BEST EMULATOR WINS` decisions require measured compatibility, performance and
integration evidence, not reputation.

### Data / Storage Engineer

**Professional alignment**
- Android storage/SAF official docs
- SQLite/data-layer official technical documentation
- secure data-handling standards where relevant

**Oz qualification**
Migration/recovery/ownership/integrity tests and restart/process-death evidence.

### QA / Test Engineer

**Professional alignment**
- ISTQB Certified Tester Foundation Level (CTFL) competency body

**Use in Oz**
- test design
- test levels/types
- traceability
- defect reasoning
- risk-based test coverage

**Oz qualification**
Must independently challenge implementation claims for R2/R3 closure.

### Performance Engineer

**Professional alignment**
- Android performance guidance
- Perfetto/system tracing official material
- measured device evidence

**Oz qualification**
Baseline → bottleneck → narrow change → remeasure.
No performance claim without measurement.

### Security / Privacy Engineer

**Professional alignment**
- ISC2 CSSLP domains for secure software lifecycle
- OWASP MASVS/MASTG for mobile
- OWASP SAMM for secure-development maturity
- relevant NIST secure-software guidance where applicable

**Important**
OWASP explicitly does not certify vendors, verifiers or software as
“OWASP certified”. Oz may demonstrate alignment/compliance evidence but must not
invent an OWASP trust mark.

### Git / Release Engineer

**Professional alignment**
- Git official documentation
- GitHub official Actions/release/security documentation
- supply-chain/security standards when relevant

**Oz qualification**
Reproducible build/release evidence, exact SHA/upstream checks and non-destructive
Git discipline.

### Technical Researcher

**Professional alignment**
No single credential is authoritative.

**Oz qualification**
Primary-source research quality:
- current official source
- publication/version date
- direct quotation/paraphrase integrity
- contradiction handling
- UNKNOWN when evidence is missing

### Game Knowledge Curator

**Professional alignment**
No recognized certification establishes universal game-title/release knowledge.

**Primary authority**
- first-party/platform holder records where available
- publisher/developer sources
- reputable archival/database evidence with provenance
- cross-source reconciliation

**Oz qualification**
Must prevent blind title normalization and false Release/platform merges.

### Console Interface Art Director

**Professional alignment**
No universal credential governs Oz's intended visual identity.

**Oz qualification**
Approved mockup fidelity, composition, coherence, console identity and review
against the Oz visual system.

### Console Interaction & Motion Designer

**Professional alignment**
Platform/controller/accessibility guidance plus interaction/motion best practice.

**Oz qualification**
Focus navigation, controller predictability, motion clarity, accessibility and
device evidence.

---

## 5. Current official reference set

Retrieved/verified for Governance V4 on 2026-08-29.

- PMI PMP:
  https://www.pmi.org/certifications/project-management-pmp/
- PMI-ACP:
  https://www.pmi.org/certifications/agile-acp
- Scrum.org PSPO:
  https://www.scrum.org/professional-scrum-product-owner-certifications
- The Open Group TOGAF Certification Portfolio:
  https://www.opengroup.org/certifications/togaf
- ISTQB CTFL:
  https://www.istqb.org/certifications/certified-tester-foundation-level-ctfl-v4-0/
- ISC2 CSSLP:
  https://www.isc2.org/Certifications/CSSLP
- OWASP MASVS:
  https://mas.owasp.org/MASVS/
- OWASP MASVS certification stance:
  https://mas.owasp.org/MASVS/04-Assessment_and_Certification/
- OWASP SAMM:
  https://owasp.org/www-project-samm/
- Google Associate Android Developer retirement notice:
  https://developers.google.com/certification/associate-android-developer

These sources are competency references. They do not themselves certify an Oz AI role.

---

## 6. Governance research influence

Governance V4 borrows structural ideas—not institutional bureaucracy—from current
university AI governance patterns:

- central governance with distributed specialist support;
- clear local policies/charters;
- human accountability;
- escalation for higher-risk use;
- ongoing review/freshness;
- support/helpdesk separated from policy authority.

Examples reviewed:
- University of Oxford GenAI governance / AI Competency Centre
- MIT Faculty Governance AI-use policy guidance

Oz adapts these patterns to a one-Product-Owner software project rather than
copying university committees.
