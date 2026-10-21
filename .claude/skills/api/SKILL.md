---
name: api
description: A change of the public API — rename, remove or reshape an option, command, hook or type — shown as before/after, then code review, implementation on every layer and the docs.
disable-model-invocation: true
argument-hint: <what to change> [flags]
---

# A change of the API

Follow `.claude/skills/task-flow/SKILL.md` with these stages:

1. **Before and after:** for each change, the API as it is and as it will be, with a short example of each; what it
   means for apps; the layers it touches (TS types, engine, iOS, Android, Showcase, docs). Platform facts only when
   the change depends on them. ⏸
2. **Decision** into the task file.
3. **Code review**, always. ⏸
4. **Implementation** in parallel. JSDoc and `docs/sections.json` change with the code; `docs-writer` updates every
   page that shows the old API.
5. **Review:** `reviewer` checks the platforms agree and no use of the old name is left (search the whole
   repository); `scripts/check.sh` and `scripts/docs.sh` pass.
6. **Report.**

The change: $ARGUMENTS
