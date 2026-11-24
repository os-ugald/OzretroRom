#!/usr/bin/env node
/*
 * Oz RetroRom — PreToolUse guard for the Bash tool.
 *
 * Blocks (exit 2) destructive / indiscriminate Git operations that the Oz
 * governance forbids without explicit Product Owner authorization.
 *
 * Governance anchors:
 *   - AGENTS.md  "Non-negotiable prohibitions" (destructive Git)
 *   - docs/AI_WORKSPACE_ROLES.md  Claude Code "Prohibited"
 *   - CLAUDE.md  "Git safety (enforced, not advisory)"
 *
 * Contract: reads the PreToolUse JSON payload on stdin. For tool_name "Bash"
 * it inspects tool_input.command. Exit 0 = allow, exit 2 = block (stderr is
 * surfaced to Claude).
 *
 * The hook only ever receives the raw command string (no tokenised form), so
 * before pattern-matching it strips inert *message text* — heredoc bodies and
 * quoted `-m` / `--message` arguments — that carries no executable command.
 * This removes false positives when a commit message merely *quotes* a blocked
 * command (e.g. documentation that mentions "git add -A") while still blocking
 * the real command and anything hidden in a command substitution.
 */

'use strict';

const RULES = [
  {
    name: 'git reset --hard',
    re: /\bgit\s+reset\s+(?:[^\n&|;]*\s)?--hard\b/,
    rule: 'Oz: "Do not use destructive Git operations without explicit authorization." '
        + 'git reset --hard discards committed and working-tree state irreversibly.',
  },
  {
    name: 'git clean -f',
    re: /\bgit\s+clean\s+(?:[^\n&|;]*\s)?-[a-eg-z]*f[a-z]*\b/,
    rule: 'Oz: "Do not silently discard recoverable partials or user-owned content." '
        + 'git clean -fd deletes untracked files, including unversioned evidence under .artifacts/.',
  },
  {
    name: 'git push --force',
    re: /\bgit\s+push\s+[^\n&|;]*(?:--force\b|--force-with-lease\b|\s-f\b)/,
    rule: 'Oz: "Do not use destructive Git operations without explicit authorization." '
        + 'Force-pushing rewrites published history on the shared remote.',
  },
  {
    name: 'destructive git rebase',
    re: /\bgit\s+rebase\b(?![^\n&|;]*--(?:abort|continue|quit|edit-todo|show-current-patch))/,
    rule: 'Oz: "Do not use destructive Git operations without explicit authorization." '
        + 'Rebase rewrites local history and can drop commits; use an explicit merge or ask the Product Owner.',
  },
  {
    name: 'indiscriminate git add',
    re: /\bgit\s+add\s+(?:-A\b|--all\b|-A\s|\.(?:\s|$)|-{1,2}[A-Za-z]*\s+\.(?:\s|$))/,
    rule: 'Oz: "Do not stage indiscriminately when local/untracked evidence exists." '
        + 'Stage files explicitly by path; .artifacts/ and tools/ must never be blanket-added.',
  },
];

/*
 * Return a copy of `command` with inert commit-message text removed, so the
 * RULES match only against constructs that can actually execute.
 *
 * Removed (following shell quoting semantics):
 *   - Heredoc bodies with a quoted delimiter (<<'EOF' / <<"EOF") — no expansion
 *     happens, the whole body is inert literal text.
 *   - Heredoc bodies with an unquoted delimiter (<<EOF) only when they contain
 *     no `$(` and no backtick (nothing that would expand / execute).
 *   - A single-quoted `-m` / `--message` argument — no expansion, inert.
 *   - A double-quoted `-m` / `--message` argument only when it contains no
 *     `$(` and no backtick.
 *
 * Preserved (still scanned):
 *   - Any construct where the shell would actually run a command substitution
 *     (`$(...)` or backticks in an expanding context).
 *   - Every other part of the command line (chaining, redirects, real git
 *     subcommands outside the message argument).
 *
 * On any internal error this falls back to the raw command (fail closed — the
 * guard keeps scanning the full string rather than skipping a check).
 */
function stripInertMessageText(command) {
  let s = command;

  // 1. Heredoc bodies:  <<[-] ['"]?DELIM['"]?  <rest-of-line>\n <body> \n <indent>DELIM
  s = s.replace(
    /<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1[^\n]*\n([\s\S]*?)\n[ \t]*\2(?![A-Za-z0-9_])/g,
    (match, delimQuote, delim, body) => {
      const expands = delimQuote === '';            // <<EOF expands; <<'EOF' does not
      if (expands && /\$\(|`/.test(body)) { return match; }  // body can execute — keep
      return `<<${delim}\n${delim}`;                // drop the inert body
    }
  );

  // 2. Quoted argument to -m / --message  (e.g.  -m "msg"  |  --message='msg')
  s = s.replace(
    /(^|\s)(-m|--message)(=|\s+)(['"])((?:\\.|(?!\4)[^\\])*)\4/g,
    (match, pre, flag, sep, quote, inner) => {
      const expands = quote === '"';                // "..." expands; '...' does not
      if (expands && /\$\(|`/.test(inner)) { return match; }  // substitution inside — keep
      return `${pre}${flag}${sep}${quote}${quote}`;
    }
  );

  return s;
}

let raw = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (c) => { raw += c; });
process.stdin.on('end', () => {
  let payload = {};
  try { payload = JSON.parse(raw || '{}'); } catch (_) { /* fail open on unparseable payload */ }

  if ((payload.tool_name || payload.toolName) !== 'Bash') { process.exit(0); }

  const input = payload.tool_input || payload.toolInput || {};
  const command = String(input.command || '');
  if (!command.trim()) { process.exit(0); }

  let scannable = command;
  try { scannable = stripInertMessageText(command); }
  catch (_) { scannable = command; /* fail closed: scan the raw string */ }

  for (const r of RULES) {
    if (r.re.test(scannable)) {
      process.stderr.write(
        `\n[OZ GOVERNANCE BLOCK] ${r.name}\n\n` +
        `Command refused by .claude/hooks/block-destructive-git.js\n\n` +
        `${r.rule}\n\n` +
        `If this is genuinely required: stop, explain why in chat, and obtain\n` +
        `explicit Product Owner authorization before the guard is lifted.\n`
      );
      process.exit(2);
    }
  }
  process.exit(0);
});
