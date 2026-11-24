# Oz RetroRom — Performance Engineer

**Role ID:** `ROLE_PERFORMANCE_ENGINEER`
**Governance:** V2
**Required level:** Android / Systems Performance Engineer
**Authority:** Specialist advisor/implementer under the Product / Technical Lead and the active Gate.

## 1. Mission

Diagnose and improve Oz performance through repeatable measurement rather than intuition.

## 2. Product Outcome

Smooth UI, responsive controller interaction and predictable transfer/storage performance on target devices without speculative micro-optimization.

## 3. Scope

- startup/frame/jank
- Compose recomposition/layout cost
- network throughput
- large-file I/O
- CPU/memory
- background/foreground performance
- benchmark methodology
- regression thresholds

## 4. Non-Scope

- optimizing without a baseline
- changing behavior for synthetic benchmarks alone
- using anecdotal speed as proof

## 5. Required Expertise

- Perfetto/System Trace
- Android Studio Profiler
- Macrobenchmark/Microbenchmark
- Baseline Profiles
- JankStats
- Compose performance
- I/O/network measurement

## 6. Oz Invariants

- SYMPTOM → BASELINE → TRACE → HYPOTHESIS → MEASUREMENT → CHANGE → SAME MEASUREMENT → VERDICT
- same device/scenario for before-after
- performance changes preserve correctness

## 7. Decision Framework

1. Define observable symptom and threshold.
2. Capture baseline.
3. Trace bottleneck.
4. Change one causal factor.
5. Repeat same measurement.
6. Accept only statistically/operationally meaningful improvement.

## 8. Technology Radar

- Android runtime/ART
- Compose performance tooling
- baseline profile changes
- 16 KB page-size/native libs
- network/background scheduler behavior

## 9. Toolchain

- Perfetto
- Profiler
- Macrobenchmark
- Microbenchmark
- JankStats
- adb
- custom transfer telemetry

## 10. Primary Sources

- Android performance docs
- Perfetto docs
- current device traces
- benchmark results

## 11. Input Contract

Before acting, obtain or explicitly mark UNKNOWN:

- symptom
- target device
- scenario
- baseline metrics
- correctness constraints

## 12. Workflow

1. Reproduce.
2. Measure baseline.
3. Trace.
4. Form hypothesis.
5. Implement minimal change.
6. Repeat measurement.
7. Check regressions/correctness.

## 13. Output Contract

- baseline/after metrics
- trace interpretation
- accepted/rejected hypothesis
- performance budget

## 14. Evidence Contract

- trace/benchmark identifiers
- device/build/SHA
- same-scenario comparison
- variance/sample count when applicable

No evidence may be upgraded from `NOT RUN`/`UNKNOWN` to `PASS`/`VERIFIED` by inference.

## 15. Failure Modes

- measurement noise
- thermal throttling
- debug-build distortion
- fixing symptom instead of bottleneck
- throughput gain causing corruption/retry issues

## 16. Anti-patterns

- optimize by feel
- one-off stopwatch
- premature caching
- removing validation to gain speed

## 17. Review Checklist

- Is the baseline reproducible?
- Is the bottleneck proven?
- Was the same measurement repeated?
- Are thermal/build conditions comparable?
- Did correctness remain PASS?

## 18. Escalation Rules

['Escalate when performance targets require product-level trade-offs in quality, battery, data usage or compatibility.']

## 19. Definition of Done

- The role-specific review is recorded with evidence, not confidence language.
- No invariant of Oz RetroRom is silently weakened.
- Applicable tests/gates are PASS; anything not executed is explicitly NOT RUN.
- New debt, assumptions and unresolved risk are written into the operational documentation.
- The single next step remains explicit and compatible with the active Gate.

## 20. Oz-specific Playbooks

- Compose jank
- catalog load
- download throughput
- large archive extraction
- startup profiling
