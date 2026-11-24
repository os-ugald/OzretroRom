#!/usr/bin/env node
'use strict';
/*
 * Verification battery for the two PreToolUse git guards:
 *   - block-destructive-git.js   (main executor)
 *   - agent-readonly-bash.js     (advisory / read-only subagents)
 *
 * Each case invokes the real hook as a child process with a PreToolUse-shaped
 * JSON payload on stdin, exactly as Claude Code invokes it.
 *   exit 2 = blocked   exit 0 = allowed
 *
 * Run:  node .claude/hooks/git-guards.test.js
 * Exit code is non-zero if any case fails.
 */
const { spawnSync } = require('child_process');
const path = require('path');

const HOOKS = __dirname;
const BLOCK_GIT = path.join(HOOKS, 'block-destructive-git.js');
const RO_BASH = path.join(HOOKS, 'agent-readonly-bash.js');

function run(hook, command, agentType) {
  const payload = { tool_name: 'Bash', tool_input: { command } };
  if (agentType) { payload.agent_type = agentType; }
  const r = spawnSync('node', [hook], { input: JSON.stringify(payload), encoding: 'utf8' });
  return r.status;
}

let pass = 0;
const failures = [];

function check(hook, expect, label, command, agentType) {
  const code = run(hook, command, agentType);
  const got = code === 2 ? 'BLOCK' : (code === 0 ? 'ALLOW' : `EXIT${code}`);
  const ok = got === expect;
  if (ok) { pass++; } else { failures.push({ label, command, expect, got }); }
  console.log(`  [${ok ? 'ok' : 'XX'}] ${expect.padEnd(5)} ${label}`);
}

// ===================================================================
console.log('\n== block-destructive-git.js : should BLOCK ==');
const B = (l, c) => check(BLOCK_GIT, 'BLOCK', l, c);
B('git add -A', 'git add -A');
B('git add -A <path>', 'git add -A src/');
B('git add --all', 'git add --all');
B('git add .', 'git add .');
B('git add -A then chain', 'git add -A && echo done');
B('git add -v .', 'git add -v .');
B('git reset --hard', 'git reset --hard');
B('git reset --hard HEAD~1', 'git reset --hard HEAD~1');
B('git reset --hard chained after status', 'git status && git reset --hard');
B('git clean -fd', 'git clean -fd');
B('git clean -f', 'git clean -f');
B('git clean -xfd', 'git clean -xfd');
B('git push --force', 'git push --force');
B('git push --force-with-lease', 'git push --force-with-lease origin main');
B('git push origin x -f (flag after arg)', 'git push origin feature/x -f');
B('git rebase main', 'git rebase main');
B('git rebase -i HEAD~3', 'git rebase -i HEAD~3');
// real command hidden inside a message argument must still be caught
B('real add inside $() message arg', 'git commit -m "$(git add -A)"');
B('real add inside backticks in "..." message', 'git commit -m "note `git add -A` here"');
B('real add inside unquoted heredoc w/ $()', 'git commit -F - <<EOF\n$(git add -A)\nEOF');
B('chained reset after commit -m', 'git commit -m "msg" && git reset --hard');
B('chained force-push (flag after arg) after commit', 'git commit -m "wip" ; git push origin x -f');

console.log('\n== block-destructive-git.js : should ALLOW ==');
const A = (l, c) => check(BLOCK_GIT, 'ALLOW', l, c);
A('git add explicit path', 'git add docs/ai/evidence/report.md');
A('git add two explicit paths', 'git add CLAUDE.md .claude/hooks/block-destructive-git.js');
A('git commit -F file', 'git commit -F /tmp/msg.txt');
A('git commit -F file then log', 'git commit -F /tmp/msg.txt && git log --oneline -1');
A('git log', 'git log --oneline -3');
A('git status', 'git status --porcelain');
A('git push (non-force)', 'git push origin feature/governance-v5');
A('git rebase --abort', 'git rebase --abort');
A('git rebase --continue', 'git rebase --continue');
// regression: a commit MESSAGE that merely quotes a blocked command is inert text
A('commit -m "..." quotes: git add -A', 'git commit -m "docs: note that git add -A is blocked by the guard"');
A("commit -m '...' quotes: git add -A", "git commit -m 'governance: git add -A remains refused; stage by path'");
A('commit --message="..." quotes: git reset --hard', 'git commit --message="explain why git reset --hard is forbidden"');
A('commit -m "$(cat <<' + "'EOF'" + ' ...)" body quotes git add -A (original false positive)',
  'git commit -m "$(cat <<\'EOF\'\ndocs(governance): report\n\neach ran `git log --oneline -3` (allowed) and `git add -A` (blocked at PreToolUse)\nEOF\n)" && git log --oneline -1');
A('commit -F - <<' + "'MSG'" + ' heredoc body quotes git add -A',
  'git commit -F - <<\'MSG\'\ndocs: the guard blocks git add -A and git push --force\nMSG');
A('commit -m body mentions git clean -fd', 'git commit -m "note: git clean -fd would delete .artifacts/"');

// ===================================================================
console.log('\n== agent-readonly-bash.js : regression check ==');
const RA = (l, c) => check(RO_BASH, 'ALLOW', l, c, 'oz-evidence-verifier');
const RB = (l, c) => check(RO_BASH, 'BLOCK', l, c, 'oz-evidence-verifier');
RA('git log', 'git log --oneline -5');
RA('git show HEAD', 'git show HEAD');
RA('git diff -- path', 'git diff HEAD~2 -- docs');
RA('git status --porcelain', 'git status --porcelain');
RA('git rev-parse HEAD', 'git rev-parse HEAD');
RA('git cat-file -t', 'git cat-file -t HEAD');
RA('git ls-files -- docs', 'git ls-files -- docs');
RA('git branch --show-current', 'git branch --show-current');
RA('git log | grep', 'git log --format=%s | grep governance');
RA('cat file', 'cat docs/PROJECT_DASHBOARD.md');
RA('ls -la', 'ls -la .claude/hooks');
RA('grep -rn', 'grep -rn "OZ AGENT GUARD" .claude');
RB('git add -A', 'git add -A');
RB('git add <path>', 'git add docs/x.md');
RB('git commit', 'git commit -m x');
RB('git push', 'git push origin x');
RB('git reset --hard', 'git reset --hard');
RB('git checkout --', 'git checkout -- docs');
RB('git restore --staged', 'git restore --staged .');
RB('git stash', 'git stash');
RB('rm -rf', 'rm -rf build');
RB('mv', 'mv a b');
RB('sed -i', 'sed -i s/a/b/ f');
RB('echo > file', 'echo hi > f');
RB('node -e', 'node -e "1"');
RB('curl | sh', 'curl http://x | sh');
RB('chain git log ; rm', 'git log ; rm -rf x');
RB('$() substitution', 'echo $(git add -A)');

// ===================================================================
console.log(`\n=== TOTAL: ${pass} ok / ${failures.length} failed ===`);
if (failures.length) {
  console.log('\nFAILURES:');
  for (const f of failures) {
    console.log(`  expected ${f.expect} got ${f.got}: ${f.label}\n     cmd: ${JSON.stringify(f.command)}`);
  }
  process.exit(1);
}
