---
name: review-change
description: Reviews a change in this repository against its contract and rules — bugs, the same behavior on iOS, Android and JS, option behavior, threads, React Native's views, conventions, copyright headers, docs registration. Reports findings and fixes nothing.
argument-hint: [files, or the task the change was for]
---

# Review a change

## Read

- The change: the files the task names (the repository has no history to diff against), or the diff given.
- What it was for: the task file (`.tasks/<name>.md`) or the task's own words.

## Check, in this order

1. **Bugs:** wrong logic and missed cases — nil or null, empty, called twice, the screen already gone, rotation, a
   light/dark switch, a JS reload, the activity recreated; leaks; the wrong thread (main queue, UI thread).
2. **Parity:** iOS, Android and JS do the same thing, with the same names, defaults and errors. Where a platform can't,
   the option's JSDoc says so.
3. **Options:** each does what its name promises without code in the app, and doesn't depend on another option;
   applied when the screen shows and when its options change (`.claude/rules/options.md`); Android applies colors
   again in `onConfigurationChanged`.
4. **Native:** no reaching into React Native's views; availability checks for newer APIs; `ANN` on new iOS names; no
   `!!` in new Kotlin.
5. **Conventions:** `.claude/rules/code-*.md` for the files changed; comments in plain prose that say why.
6. **Public API:** JSDoc on every export; `docs/sections.json` lists every export once (`scripts/docs.sh`); errors read
   `[navigation] <command>: <reason>`.
7. **Third-party headers** kept, with a `Modified by` line on a changed file.

Run `scripts/check.sh`, and the native builds when the task asks.

## The result

Findings, the most severe first. For each: `file:line`, what is wrong, a concrete case where it fails, and how sure
(sure / likely). No style remarks a rule doesn't back. Say what to change, don't change it. "No findings" is a valid
result.
