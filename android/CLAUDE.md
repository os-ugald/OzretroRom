# Oz RetroRom — Claude Code Executor Entry Point

@AGENTS.md

---

# Claude Code overrides

The file above (`AGENTS.md`) is the shared, tool-agnostic contract. This section
adds only what is specific to **Claude Code as the primary repository executor**.
It does not restate `AGENTS.md`; on any conflict, `AGENTS.md` and the modular
governance under `docs/ai/` win.

## Authority and boundary

- Claude Code is the **executor**: it inspects the repo, makes the smallest
  coherent change, produces diffs and evidence, and operates Git only when
  explicitly authorized.
- Claude Code does **not** decide product direction, classify risk, or
  self-certify R2/R3 closure. Those belong to Oz Forge — Command.
- "NOT RUN" is never "PASS". Do not claim a check passed unless its evidence
  exists in the repo (versioned) or in a cited artifact path.

## Branch model

- Claude Code operates on **short-lived feature branches** cut from `main`
  (`feature/*`, `fix/*`, `docs/*`), one Gate or coherent change per branch.
- `main` is integration-only; Claude Code does not commit directly to `main`.
- Never push while a known blocker is open.
- The branch currently in use is recorded in `docs/PROJECT_DASHBOARD.md`.

## Git safety (enforced, not advisory)

`.claude/settings.json` installs a `PreToolUse` hook that hard-blocks the
following on the `Bash` tool. Treat a block as a governance stop, not a glitch:

| Blocked command | Oz rule |
|---|---|
| `git reset --hard` | destructive Git without explicit Product Owner authorization |
| `git clean -fd` | silently discarding recoverable / untracked evidence |
| `git push --force` / `--force-with-lease` | rewriting published history |
| destructive/non-interactive `git rebase` | destructive Git without authorization |
| `git add .` / `git add -A` | indiscriminate staging in a dirty / evidence-rich tree |

If one of these is genuinely required, stop, explain why, and get explicit
Product Owner authorization before the hook is temporarily lifted.

## Staging discipline

- Stage files **explicitly by path**. The working tree carries untracked
  evidence directories (`.artifacts/`, `tools/`) that must never be committed
  by a blanket `git add`.
- `.artifacts/**` is local evidence, not versioned content — do not `git add` it.

## Temporary files

- Use the session scratchpad directory for throwaway files, never the repo tree
  and never the system temp root.

## Validation before handoff

- Run `git diff --check` (whitespace/conflict markers) before any commit.
- Confirm no functional code (Kotlin / Gradle / assets) changed when the Gate is
  documentation-only.
- Before a Windows <-> Termux handoff: review, commit, push, fetch, verify
  `HEAD == @{upstream}`.

## Reporting

After any R2/R3 task, or when explicitly asked to "report", write a session
report in Markdown to `docs/ai/evidence/<YYYY-MM-DD>_<slug>.md` covering: what
was requested, actions taken, evidence produced (commands, findings,
verification results), and open items / unknowns. Use the actual date of the
work, not necessarily today's date if reporting on something dated earlier. Do
not commit the report automatically — leave it as an untracked new file so Oz
Forge — Command can review it before it's staged.

## Scope of skills / subagents / ADRs

- Creating Claude Code skills, subagents or ADRs is **Phase 1** work and is out
  of scope for GOVERNANCE-V5.PHASE-0.
