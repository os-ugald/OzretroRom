# OBSERVABILITY_DIAGNOSTICS_PROTOCOL

## Purpose
Make critical Oz operations diagnosable from structured state instead of guesswork.

## Required operation fields
`correlation/task id`, state, phase, elapsed time, bytes/progress, source class, transport class, error type, retry count, failover state, network class, foreground/background and relevant artifact identity.

## Diagnostic Bundle
- Git SHA and app/build
- device/API level
- exact reproduction steps
- structured logs/events
- state snapshot
- expected vs actual
- screenshots when useful
- no credentials, tokens, private URLs or other secrets

## Rules
1. Diagnose the state transition that failed before editing code.
2. Preserve typed errors; do not collapse all failures into generic messages.
3. Correlate retries/failover with the same logical task while preserving source-specific transfer identity.
4. Telemetry must be useful without containing secrets.
5. A critical background/download/launch bug should produce a reusable diagnostic path, not a one-off log statement.
