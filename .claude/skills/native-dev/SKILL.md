---
name: native-dev
description: How a native change is made in ios/ or android/ — the draft mode (complete code for review, nothing written) and the write mode, the folders a change may touch, and the build that proves it. Use when implementing or changing native code.
argument-hint: <the task> [draft]
---

# A native change

## Modes

- **Draft** (the task says "draft"): return the complete code of the change — a unified diff per changed file, whole
  new files — and a short why for each part. Write nothing.
- **Write** (otherwise): make the change, then check it (below).

## Before writing

- The contract is fixed: names, JSON, defaults and behavior per platform come from the task or its task file
  (`.tasks/<name>.md`). If it can't work as written, stop and say why; don't change it on your own.
- Read the map (`ios/CLAUDE.md` or `android/CLAUDE.md`) and the code around the change. `CORE.md` of the same folder
  when the change touches the core's structure or a command's path; `RECIPES.md` when it adds an option or a command. Follow
  `.claude/rules/code-objc.md` or `code-kotlin.md`, and `options.md` / `native-contract.md` when they apply.
- A platform fact you aren't sure of: the `native-facts` skill.

## Limits

- iOS: only `ios/` and `example/ios/`. Android: only `android/` and `example/android/`. What else the change needs (JS,
  docs, the other platform) goes into the result, not into other folders.
- No commits, no new dependencies unless the task says so, no reaching into React Native's views.

## Check

- iOS: `scripts/build-ios.sh`, OK with no warnings. A new file: `scripts/pods.sh` first; a new folder also goes into
  `headerFolders` of `ios/Package.swift`.
- Android: `scripts/build-android.sh --lib`.
- A changed codegen spec: `scripts/pods.sh` for iOS; the Gradle build regenerates Android's.

## The result

The files changed, what and why in a few lines, the check's line, and what is left for others.
