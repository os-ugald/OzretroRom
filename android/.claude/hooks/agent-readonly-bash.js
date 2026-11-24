#!/usr/bin/env node
/*
 * Oz RetroRom — read-only Bash guard for advisory subagents.
 *
 * Referenced from the frontmatter of:
 *   .claude/agents/oz-evidence-verifier.md   (R0, Haiku)
 *   .claude/agents/oz-researcher.md          (R0/R1, Sonnet)
 *   .claude/agents/oz-assurance.md           (R2/R3, Opus — advisory only)
 * as a `PreToolUse` hook with matcher "Bash". It is ADDITIVE to the global
 * `.claude/settings.json` destructive-git hook — both run for these subagents.
 *
 * Why this exists: the subagent `tools:` allowlist controls WHICH tools the
 * agent may invoke, not what runs inside `Bash`. Without this guard, a
 * Bash-enabled advisory agent has a full shell (rm, git commit/push, gradle,
 * curl, write redirects, ...). These three agents are consultative / read-only
 * by role, so their shell is restricted to inspection commands here.
 *
 * Model: DENY BY DEFAULT. A command is allowed only if it is a single pipeline
 * whose every stage invokes a known read-only executable (and, for git, a
 * known read-only subcommand). No command chaining, no substitution, no
 * writes, no network, no interpreters.
 *
 * Contract: reads the PreToolUse JSON payload on stdin. Exit 0 = allow,
 * exit 2 = block (stderr surfaced to Claude). Fails CLOSED: anything that
 * cannot be parsed or classified is blocked.
 */

'use strict';

// Read-only top-level executables. Anything not here is blocked.
const READ_ONLY_CMDS = new Set([
  'git', 'cat', 'ls', 'find', 'grep', 'egrep', 'fgrep', 'rg', 'head', 'tail',
  'wc', 'nl', 'cut', 'sort', 'uniq', 'comm', 'diff', 'colordiff', 'tac',
  'stat', 'file', 'tree', 'pwd', 'echo', 'printf', 'basename', 'dirname',
  'realpath', 'readlink', 'du', 'df', 'date', 'printenv', 'hostname',
  'whoami', 'id', 'uname', 'which', 'type', 'true', 'false', 'test', '[',
  'sha1sum', 'sha256sum', 'md5sum', 'cksum', 'jq', 'yq', 'xxd', 'od',
  'strings', 'column', 'seq', 'expr',
]);

// git subcommands that are read-only in every mode.
const GIT_READONLY = new Set([
  'log', 'show', 'diff', 'status', 'rev-parse', 'rev-list', 'cat-file',
  'blame', 'annotate', 'ls-files', 'ls-tree', 'for-each-ref', 'show-ref',
  'show-branch', 'shortlog', 'name-rev', 'merge-base', 'whatchanged',
  'grep', 'count-objects', 'var', 'describe', 'range-diff', 'cherry',
  'verify-commit', 'verify-tag', 'help', 'version', 'rev-list',
]);

// git global options that take a following argument (to be skipped).
const GIT_OPTS_WITH_ARG = new Set(['-C', '-c', '--git-dir', '--work-tree', '--namespace', '--exec-path']);

function block(reason) {
  process.stderr.write(
    `\n[OZ AGENT GUARD] read-only Bash — command refused\n\n` +
    `Refused by .claude/hooks/agent-readonly-bash.js\n\n` +
    `This subagent is consultative / read-only. Its shell is limited to\n` +
    `inspection commands (git log/show/diff/status/rev-parse/cat-file/blame,\n` +
    `cat, ls, find, grep, head, tail, wc, jq, sha256sum, ...), optionally in a\n` +
    `single pipeline. No writes, deletes, moves, commits, pushes, staging,\n` +
    `redirects, interpreters or network.\n\n` +
    `Blocked: ${reason}\n\n` +
    `If a mutating action is genuinely required, hand it back to the main\n` +
    `Claude Code session / Oz Forge — Command with an explicit request.\n`
  );
  process.exit(2);
}

// Remove quoted spans so shell operators inside quotes are not misread.
function stripQuoted(s) {
  return s
    .replace(/'[^']*'/g, ' ')
    .replace(/"(\\.|[^"\\])*"/g, ' ');
}

// Minimal quote-aware whitespace tokenizer for a single pipeline stage.
function tokenize(stage) {
  const out = [];
  let cur = '';
  let q = null;
  for (let i = 0; i < stage.length; i++) {
    const ch = stage[i];
    if (q) {
      if (ch === q) { q = null; }
      else { cur += ch; }
      continue;
    }
    if (ch === '"' || ch === "'") { q = ch; continue; }
    if (ch === ' ' || ch === '\t') { if (cur) { out.push(cur); cur = ''; } continue; }
    cur += ch;
  }
  if (q) { return null; } // unbalanced quotes -> caller blocks
  if (cur) { out.push(cur); }
  return out;
}

function checkGit(tokens) {
  // tokens[0] === 'git'
  let i = 1;
  while (i < tokens.length) {
    const t = tokens[i];
    if (t === '--version' || t === '--help') { return; }
    if (GIT_OPTS_WITH_ARG.has(t)) { i += 2; continue; }
    if (t.startsWith('--') && t.includes('=')) { i += 1; continue; }
    if (t.startsWith('-')) { i += 1; continue; }
    break;
  }
  const sub = tokens[i];
  if (!sub) { return; } // bare `git` — harmless usage/help
  const rest = tokens.slice(i + 1);

  if (GIT_READONLY.has(sub)) { return; }

  // Conditionally-safe subcommands: allow only clearly read-only forms.
  if (sub === 'branch') {
    if (rest.some((a) => !a.startsWith('-'))) { block(`git branch with a name argument (creates/moves a branch): ${tokens.join(' ')}`); }
    if (rest.some((a) => /^(-d|-D|-m|-M|-c|-C|-f|--delete|--move|--copy|--force|--set-upstream-to|--edit-description|-u)$/.test(a))) {
      block(`git branch mutating flag: ${tokens.join(' ')}`);
    }
    return;
  }
  if (sub === 'tag') {
    const okFlags = rest.every((a) => /^(-l|--list|-n\d*|--contains|--no-contains|--points-at|--merged|--no-merged|--sort|--format|--color|--column|--omit-empty|--ignore-case|-i)$/.test(a) || a.startsWith('--sort=') || a.startsWith('--format='));
    if (!okFlags) { block(`git tag non-list form (may create/delete a tag): ${tokens.join(' ')}`); }
    return;
  }
  if (sub === 'config') {
    const readOnly = rest.some((a) => /^(--get|--get-all|--get-regexp|--get-urlmatch|--list|-l)$/.test(a));
    const mutating = rest.some((a) => /^(--add|--unset|--unset-all|--replace-all|--rename-section|--remove-section|--edit|-e)$/.test(a));
    if (mutating || !readOnly) { block(`git config non-read form: ${tokens.join(' ')}`); }
    return;
  }
  if (sub === 'remote') {
    if (rest.length === 0) { return; }
    if (rest.length === 1 && /^(-v|--verbose)$/.test(rest[0])) { return; }
    if (rest[0] === 'get-url') { return; }
    block(`git remote subcommand (show does network I/O; add/set-url/prune mutate): ${tokens.join(' ')}`);
  }
  if (sub === 'reflog') {
    if (rest.length === 0 || rest[0] === 'show') { return; }
    block(`git reflog non-show form: ${tokens.join(' ')}`);
  }
  if (sub === 'stash') {
    // bare `git stash` == `git stash push` (mutating). Only list/show are safe.
    if (/^(list|show)$/.test(rest[0] || '')) { return; }
    block(`git stash non-list/show form (bare 'git stash' pushes a stash): ${tokens.join(' ')}`);
  }
  block(`git subcommand not on the read-only allowlist: '${sub}'`);
}

function checkStage(stageRaw) {
  const tokens = tokenize(stageRaw.trim());
  if (tokens === null) { block(`unbalanced quotes in: ${stageRaw.trim()}`); }
  if (tokens.length === 0) { block('empty pipeline stage'); }

  // Skip leading VAR=value assignments and a leading `env`.
  let idx = 0;
  while (idx < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[idx])) { idx += 1; }
  if (tokens[idx] === 'env') { idx += 1; while (idx < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[idx])) { idx += 1; } }

  const raw = tokens[idx];
  if (!raw) { block(`no command in stage: ${stageRaw.trim()}`); }
  const cmd = raw.replace(/^.*[\\/]/, ''); // basename
  const args = tokens.slice(idx + 1);

  if (!READ_ONLY_CMDS.has(cmd)) { block(`'${cmd}' is not on the read-only allowlist`); }

  if (cmd === 'git') { checkGit([cmd, ...args]); return; }

  if (cmd === 'find') {
    if (args.some((a) => /^-(exec|execdir|ok|okdir|delete|fprint|fprintf|fls)$/.test(a))) {
      block(`find action flag that can run commands or delete files: ${stageRaw.trim()}`);
    }
  }
  if (cmd === 'sort' && args.some((a) => a === '-o' || a === '--output' || a.startsWith('--output='))) {
    block(`sort -o writes a file: ${stageRaw.trim()}`);
  }
  if (cmd === 'date' && args.some((a) => a === '-s' || a === '--set' || a.startsWith('--set='))) {
    block(`date --set changes the system clock: ${stageRaw.trim()}`);
  }
  if ((cmd === 'xxd' || cmd === 'od') && args.includes('-r')) {
    block(`${cmd} -r reconstructs binary output (write intent): ${stageRaw.trim()}`);
  }
}

let raw = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (c) => { raw += c; });
process.stdin.on('end', () => {
  let payload;
  try { payload = JSON.parse(raw || '{}'); } catch (_) { block('unparseable hook payload'); return; }

  if ((payload.tool_name || payload.toolName) !== 'Bash') { process.exit(0); }

  const input = payload.tool_input || payload.toolInput || {};
  const command = String(input.command || '');
  if (!command.trim()) { process.exit(0); }

  // Operator scan on a quote-stripped copy.
  let scan = stripQuoted(command);
  // Permit only these redirection forms, then remove them before the scan.
  scan = scan
    .replace(/\d?>&\d/g, ' ')          // 2>&1, 1>&2, >&2
    .replace(/\d?>\s*\/dev\/null/g, ' '); // 2>/dev/null, >/dev/null

  const forbidden = [
    [/`/, 'backtick command substitution'],
    [/\$\(/, '$( ) command substitution'],
    [/\$\{/, '${ } parameter expansion'],
    [/<\(/, '<( ) process substitution'],
    [/>\(/, '>( ) process substitution'],
    [/>>/, 'append redirect'],
    [/>/, 'output redirect to a file'],
    [/;/, 'command chaining with ;'],
    [/&&/, 'command chaining with &&'],
    [/\|\|/, 'command chaining with ||'],
    [/&/, 'background execution / trailing &'],
    [/\n/, 'multiple lines / newline'],
    [/\bsudo\b/, 'sudo'],
  ];
  for (const [re, name] of forbidden) {
    if (re.test(scan)) { block(`${name} is not allowed for a read-only subagent`); }
  }

  // Single pipeline: validate each stage.
  const stages = scan.split('|');
  // Re-derive stage text from the ORIGINAL command by the same split positions
  // is fragile; instead re-split the original on unquoted '|'.
  const originalStages = splitPipelineOriginal(command);
  const list = originalStages || stages;
  for (const st of list) {
    if (!st.trim()) { block('empty pipeline stage'); }
    checkStage(st);
  }
  process.exit(0);
});

// Split the original command on top-level (unquoted) '|', excluding '||'
// (already rejected above) so each stage keeps its real quoting for tokenizing.
function splitPipelineOriginal(s) {
  const stages = [];
  let cur = '';
  let q = null;
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (q) { cur += ch; if (ch === q) { q = null; } continue; }
    if (ch === '"' || ch === "'") { q = ch; cur += ch; continue; }
    if (ch === '|') { stages.push(cur); cur = ''; continue; }
    cur += ch;
  }
  if (q) { return null; }
  stages.push(cur);
  return stages;
}
