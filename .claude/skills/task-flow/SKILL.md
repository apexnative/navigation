---
name: task-flow
description: The stages, stops and flags shared by /feature, /api and /fix — discussion, decision, code review, implementation, device check, review, report — and the task file the agents read. Those commands follow it; it isn't run on its own.
user-invocable: false
---

# How a task runs

## Stages

1. **Discussion.** Facts first: `researcher` agents in parallel, or the `native-facts` skill for one question. Then
   the options, а/б, with a recommendation, each explained: what it is, how it works now, what the platforms offer.
   ⏸ The user chooses.
2. **Decision.** What was decided: the API (names, types, defaults), the behavior on iOS and on Android, what is not
   done. It goes into the task file. ⏸ The user approves.
3. **Code review.** The TS contract (types and JSDoc) and the JS before they are written, shown in full with a
   description in the user's language. Native code is drafted for review only with `--code`: `ios-dev` /
   `android-dev` in draft mode, and later the same agent writes its draft (`SendMessage`), so the code isn't made
   twice. ⏸ The user approves.
4. **Implementation,** in parallel: `ios-dev` (`ios/`), `android-dev` (`android/`), the main session (`src/`, the
   Showcase scenario), `docs-writer` (the docs). Each checks its part with its script.
5. **Device check.** `qa` runs the scenario on the iOS simulator and an Android emulator and reports what it saw; the
   main session doesn't read device logs itself. Skipped with `--no-device`.
6. **Review.** `reviewer` against the task file. The owner of a file fixes its findings; a fix that wasn't small is
   reviewed again.
7. **Report.** 3–5 lines in the user's language: what was done, how it was checked, what is left. A commit only when
   the user says (the `git` skill).

When the request is an item of the backlog, its status follows the stages: in progress when the work starts, on
review at the report, done when the user accepts it.

## The task file

`.tasks/<short-name>.md`, ignored by git: the goal; the decisions and why; the contract (TS types, JSON, defaults,
behavior per platform); what is out of scope; which agent owns which files. Agents read it — they don't see the
conversation. Update it when a decision changes; delete it when the task is done.

## Flags

A flag at the end of the command, or the same in words, changes the defaults:

| Flag                  | Effect                                                   |
| --------------------- | -------------------------------------------------------- |
| `--code`              | stop for code review, native drafts included             |
| `--cause`             | stop when the cause is found, before any fix             |
| `--backlog`           | write the item into the backlog and stop                 |
| `--ios` / `--android` | one platform only                                        |
| `--no-docs`           | no `docs-writer`                                         |
| `--no-device`         | no check on a device or simulator; build and review only |

## Working rules

- **Simple work goes the quick way.** Simple: the cause is clear, the fix is a few files with a known pattern (a
  condition, a missing case, a default, dead code, a JSDoc or a doc line), on one platform or both. The main session
  makes it itself, JS and native alike, with the rules of `native-dev` and its build script: no task file, no `ios-dev`
  / `android-dev`, no `researcher` unless a platform fact is unknown. Several simple items of one request are done one
  after another and reviewed once at the end.
- Agents are for work that is large, runs in parallel, is noisy (SDK headers, logs, long pages) or needs fresh eyes
  (review). A device run goes to `qa`, a failed CI run to `ci-doctor`: their logs stay out of the main session.
- An agent gets a precise task: the task file, the files to touch, what to return. One `ios-dev` and one
  `android-dev` per request: the next piece of work goes to the same agent (`SendMessage`), which keeps what it read,
  not to a new one.
- A question to `researcher` is only what decides the fix, not a survey around it.
- **Review once, at the end of the request:** `review-change` in the main session for simple work, one `reviewer` for
  the large parts. Findings are fixed, then the report.
- After a bug of a kind that can happen again, one line goes into the pitfalls of `ios/CLAUDE.md` or
  `android/CLAUDE.md`, or into a rule.
