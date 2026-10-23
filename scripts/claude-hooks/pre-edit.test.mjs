// Runs pre-edit.mjs over sample events: the agent folder scopes in the main checkout and in a worktree.
// Usage: node --test scripts/claude-hooks/pre-edit.test.mjs
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const HOOK = fileURLToPath(new URL('./pre-edit.mjs', import.meta.url));
const ROOT = '/repo';
const WORKTREE = `${ROOT}/.claude/worktrees/cool-name`;

const run = (agent, file) =>
  spawnSync('node', [HOOK], {
    // The file doesn't exist, so the copyright check has nothing to compare with.
    input: JSON.stringify({
      tool_name: 'Write',
      agent_type: agent,
      tool_input: { file_path: file, content: 'x' },
    }),
    env: { ...process.env, CLAUDE_PROJECT_DIR: ROOT },
    encoding: 'utf8',
  });

const cases = [
  // [agent, file, allowed]
  ['ios-dev', `${ROOT}/ios/Core/Foo.mm`, true],
  ['ios-dev', `${ROOT}/android/Foo.kt`, false],
  ['ios-dev', `${WORKTREE}/ios/Core/Foo.mm`, true],
  ['ios-dev', `${WORKTREE}/example/ios/Foo.mm`, true],
  ['ios-dev', `${WORKTREE}/android/Foo.kt`, false],
  ['ios-dev', `${WORKTREE}/src/api/index.ts`, false],
  ['android-dev', `${WORKTREE}/android/src/Foo.kt`, true],
  ['android-dev', `${WORKTREE}/ios/Foo.mm`, false],
  ['docs-writer', `${WORKTREE}/docs/page.md`, true],
  ['docs-writer', `${WORKTREE}/.claude/agents/ios-dev.md`, false],
  ['docs-writer', `${WORKTREE}/.tasks/task.md`, false],
  ['reviewer', `${WORKTREE}/ios/Foo.mm`, false],
  [undefined, `${WORKTREE}/src/api/index.ts`, true],
  [undefined, `${ROOT}/src/api/index.ts`, true],
];

for (const [agent, file, allowed] of cases) {
  test(`${agent ?? 'main session'} ${allowed ? 'may' : "can't"} change ${file.replace(ROOT, '')}`, () => {
    assert.equal(run(agent, file).status, allowed ? 0 : 2);
  });
}
