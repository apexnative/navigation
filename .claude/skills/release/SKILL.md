---
name: release
description: Prepares a release of @apexnative/navigation — checks, the next version, release notes — up to the command the maintainer runs. Never publishes.
disable-model-invocation: true
argument-hint: [version, or patch | minor | major]
---

# Prepare a release

Publishing is the maintainer's: `yarn release` (release-it: a `chore: release <version>` commit, the tag
`v<version>`, npm and a GitHub release, notes from conventional commits) and `npm publish` are denied to Claude.

1. **State:** the branch is `main`, the tree is clean, CI passed for the last commit
   (`gh run list --branch main --limit 3`).
2. **Checks:** `scripts/check.sh`, `scripts/build-ios.sh`, `scripts/build-android.sh`, `scripts/docs.sh build`.
3. **Version:** the commits since the last tag (`git log <last tag>..HEAD --oneline`), grouped as breaking changes,
   features and fixes; the next version by semver (the argument, when given, wins). The docs' version banner follows
   it by itself; the first pre-release of a new major after a stable one keeps the released docs first (the `docs`
   skill, "Versions and the banner").
4. **Notes:** a draft of the release notes from those groups, in plain words, for the maintainer to read.
5. **Hand over:** what was checked, the version, the notes, and the command to run: `yarn release`.
