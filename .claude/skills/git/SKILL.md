---
name: git
description: Commits, branches and pull requests of this repository, and what to do when GitHub Actions fails — conventional commits under commitlint, one behavior per commit, the pre-commit checks, the pull request text, the merge queue. Use before any commit, branch, push or pull request, and when CI or the nightly build fails.
argument-hint: [commit | pr | ci <run, or PR number>]
---

# Git and GitHub

Every commit, push and pull request waits for the user's word (`git commit` and `git push` ask, `.claude/settings.json`).
Never `--no-verify`, never force-push `main`, never rewrite history that was pushed.

## Commits

- **Conventional commits**, checked by commitlint (`@commitlint/config-conventional`) in the commit-msg hook: `feat`,
  `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`; `!` after the type or a `BREAKING CHANGE:`
  footer for a breaking change. release-please builds the changelog and the next version from them, so the type says
  what an app sees: a change only to the Showcase, the docs, `scripts/` or `.claude/` is never `feat` or `fix`. An
  optional scope names the area: `ios`, `android`, `cli`, `docs`, `example`, or an API area (`tabs`, `linking`).
- **Subject:** imperative, lowercase, no period, under 100 characters. **Body:** why, wrapped at 100 columns.
- **One behavior change per commit.** A fix and the refactor it needed are two commits; the iOS and Android halves of
  one behavior are one.
- **Stage by name** (`git add <paths>`), never `git add -A`: the tree may hold other work in progress. Read
  `git status` and `git diff --staged` before committing.
- **The pre-commit hook** (lefthook) runs ESLint on the staged files, `tsc` and the header check. When it fails, fix the
  cause and commit again.
- No attribution lines (`attribution` in `.claude/settings.json`).

## Branches and pull requests

- `main` takes changes through pull requests, CI and the merge queue (`merge_group` in `ci.yml`). A branch is
  `<type>/<short-slug>`: `fix/stack-double-pop`.
- A pull request is one change, titled like a commit subject: it is squashed into one commit named by its title, and
  that title is its line in the changelog (`pr-title.yml` checks it with commitlint). Its body: what changes for an
  app, why, the platforms, how it was checked (the scripts, the device checks), what is not done; `Fixes #123` for an
  issue.
- After the pull request is open, CI is watched through the app, not by polling.

## When CI fails

- Give the run to the `ci-doctor` agent (its URL or id, or the pull request's number). It reads the log and returns the
  failing job, the error, the likely cause and the command that reproduces it. A whole CI log never goes into the main
  session.
- Reproduce with the matching script before fixing: lint and types → `scripts/check.sh`; Jest → `scripts/test-js.sh`;
  the Android example → `scripts/build-android.sh`; the iOS examples → `scripts/build-ios.sh`. The nightly's CocoaPods
  variants and its Expo app have no local script: say so.
- Fix the cause in the code; change a workflow only when the workflow is what is wrong (`.claude/rules/ci.md`).

## Releases

A release is the release pull request that `release.yml` keeps open: `/release` (`.claude/skills/release/SKILL.md`).
Merging it and approving the npm publication stay the maintainer's.
