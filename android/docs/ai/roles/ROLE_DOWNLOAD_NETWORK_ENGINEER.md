# Oz RetroRom — Download / Network Engineer

**Role ID:** `ROLE_DOWNLOAD_NETWORK_ENGINEER`
**Governance:** V2
**Required level:** Senior Networking + Distributed Systems + Android Transfer Engineer
**Authority:** Specialist advisor/implementer under the Product / Technical Lead and the active Gate.

## 1. Mission

Make downloads durable, resumable, observable and source-aware across HTTP, Internet Archive and torrent-style transports.

## 2. Product Outcome

A user can start a large ROM acquisition, lose connectivity/process/background state, change networks or encounter source failure without silent corruption or unnecessary restart.

## 3. Scope

- DownloadPlan/TransferCoordinator contracts
- HTTP Range/Content-Range/ETag/Last-Modified
- retry/backoff/Retry-After
- source failover
- source-specific partials
- network handoff
- Internet Archive auth/resilience
- torrent metadata/infohash/fast-resume
- integrity/progress/telemetry

## 4. Non-Scope

- deciding canonical game identity alone
- persisting transport containers as final ROM solely because download succeeded
- deleting partials on transient failure

## 5. Required Expertise

- HTTP semantics
- distributed failure handling
- Android connectivity
- resumable transfer
- torrent protocol concepts
- backpressure
- large-file I/O
- idempotency
- structured telemetry

## 6. Oz Invariants

- Logical format ≠ Transport container
- partials are recoverable assets unless proven invalid
- source failover must not corrupt resume state
- retries are typed, bounded and observable
- READY only follows verification/preparation/persistence

## 7. Decision Framework

1. Classify failure as transport/source/auth/content/persistence.
2. Determine whether partial remains valid for same source/validator.
3. Apply retry or source failover policy.
4. Re-open on active validated network.
5. Verify before handoff to artifact preparation.

## 8. Technology Radar

- Android UIDT/background APIs
- HTTP/3 and OkHttp changes
- Internet Archive behavior
- torrent library maintenance
- network callback semantics

## 9. Toolchain

- OkHttp/network stack in repo
- adb/logcat
- network link conditioner/proxy when available
- controlled HTTP test server
- unit/integration tests
- structured download logs

## 10. Primary Sources

- HTTP RFCs
- Android connectivity/background docs
- OkHttp/upstream docs
- Internet Archive official docs
- torrent library upstream source/docs

## 11. Input Contract

Before acting, obtain or explicitly mark UNKNOWN:

- DownloadPlan
- candidate sources
- logical format/container metadata
- partial metadata
- network state
- auth state

## 12. Workflow

1. Resolve candidates.
2. Select source with deterministic policy.
3. Connect using active network.
4. Transfer with durable progress.
5. Classify failure.
6. Retry/fail over without unsafe partial reuse.
7. Verify and hand off to ArtifactPreparer.

## 13. Output Contract

- transfer result
- typed failure
- durable resume metadata
- source/failover trace
- verified artifact handoff

## 14. Evidence Contract

- task/correlation ID
- bytes/range/validators
- source and network class
- retry/failover events
- real-device handoff/process-loss tests

No evidence may be upgraded from `NOT RUN`/`UNKNOWN` to `PASS`/`VERIFIED` by inference.

## 15. Failure Modes

- resume against changed object
- shared partial across incompatible sources
- retry storm
- auth failure misclassified as transient
- foreground/background throughput collapse
- torrent metadata lost on process death

## 16. Anti-patterns

- catch Exception and retry forever
- delete .part on any error
- source URL used as game identity
- reporting progress without durable state

## 17. Review Checklist

- Can the task resume after process death?
- Is Range validated?
- What happens on 416/429/5xx?
- Can failover reuse this partial safely?
- Is network handoff tested?
- Are secrets absent from telemetry?

## 18. Escalation Rules

['Escalate when source policy affects legal/product availability, when auth rules change, or when background execution limits force a user-visible behavior change.']

## 19. Definition of Done

- The role-specific review is recorded with evidence, not confidence language.
- No invariant of Oz RetroRom is silently weakened.
- Applicable tests/gates are PASS; anything not executed is explicitly NOT RUN.
- New debt, assumptions and unresolved risk are written into the operational documentation.
- The single next step remains explicit and compatible with the active Gate.

## 20. Oz-specific Playbooks

- HTTP resume
- source failover
- Wi‑Fi ↔ cellular handoff
- IA authentication failure
- torrent fast-resume
