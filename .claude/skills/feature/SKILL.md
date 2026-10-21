---
name: feature
description: A new feature from discussion to report — facts, options, decision, code review, implementation on iOS, Android and JS, review, docs.
disable-model-invocation: true
argument-hint: <what the feature should do> [flags]
---

# A new feature

Follow `.claude/skills/task-flow/SKILL.md` with all its stages and all three stops.

1. **Facts:** what UIKit and Material offer for this, what the library already has, how React Native takes part.
   Separate questions go to `researcher` agents in parallel.
2. **Options:** the API with a short usage example, the behavior on each platform, а/б with a recommendation, what
   changes on each layer (TS types, engine, iOS, Android, Showcase, docs). ⏸
3. **Decision** into the task file. ⏸
4. **Code review:** the TS contract (types and JSDoc) and the JS; the native drafts with `--code`. ⏸
5. **Implementation:** `ios-dev`, `android-dev`, the main session (JS and a Showcase scenario), `docs-writer`, in
   parallel.
6. **Device check** by `qa` on both platforms, from the new scenario.
7. **Review** by `reviewer`, fixes, then the report.

The feature: $ARGUMENTS
