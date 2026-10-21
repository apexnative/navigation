---
name: fix
description: A bug from report to fix — understand, reproduce on a device, confirm it is the library's, find the root cause, fix on the platforms it affects, check on a device, review, report. Stops only when the fix changes behavior an app can see or the API.
disable-model-invocation: true
argument-hint: '<scenario>, <platform>: <what happens> | #<issue> [flags]'
---

# A bug

Flags and the task file: `.claude/skills/task-flow/SKILL.md`. With `--backlog`, write the bug into the backlog (the
label `Баг`) and stop. `#<number>` is a GitHub issue: read it with `gh issue view <number>`; it follows the bug
template (`.github/ISSUE_TEMPLATE/bug_report.yml`).

1. **Understand:** the scenario (`example/src/sections/…`), the code path (the maps: `CLAUDE.md`, `ios/CLAUDE.md`,
   `android/CLAUDE.md`), the platform. The bug's item in the backlog, when it has one, goes to in progress.
2. **Reproduce:** the `qa` agent runs the scenario on the platform of the report and returns what it saw, the steps,
   the decisive log lines and screenshots. A bug of a build, the types or a script is reproduced by its script, with
   no device. Not reproduced: say so with what `qa` did, and stop.
3. **Is it the library?** Check how the platform itself behaves (the `native-facts` skill, or `researcher` when the
   question is large) before calling it a bug. A platform's own behavior is reported as such.
4. **The root cause,** in one or two sentences. Stop here with `--cause`, and always when the fix changes behavior an
   app can see or the API: show the cause and the proposed fix, and wait.
5. **The fix** on the platforms it affects. A simple fix (task-flow, "Working rules") the main session makes itself,
   native code too, with the `native-dev` rules and the build scripts. A large one: `ios-dev` and `android-dev` in
   parallel, JS in the main session. With `--code`, show the code first.
6. **Check on a device:** the same `qa` agent (`SendMessage`: it keeps the device and the steps) runs the steps again
   — on both platforms when the fix touches both, otherwise on the bug's platform, with the other platform's build.
   Skipped with `--no-device`.
7. **Review,** once for all the bugs of the request, at the end: the `review-change` skill for simple fixes, the
   `reviewer` agent for a large one. The build scripts and `scripts/check.sh` pass.
8. **Pitfall:** if this kind of bug can happen again, one line in the pitfalls of `ios/CLAUDE.md` or
   `android/CLAUDE.md`.
9. **Report,** 3–5 lines: what was wrong, why, what changed, how it was checked. The backlog item goes on review.

The bug: $ARGUMENTS
