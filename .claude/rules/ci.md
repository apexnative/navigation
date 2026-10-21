---
paths:
  - '.github/**'
---

# GitHub Actions

- `ci.yml` runs on pushes and pull requests to `main` and in the merge queue: lint (ESLint, `tsc`, headers), Jest, the
  package build (`yarn prepare`), the Android example, the iOS example with static libraries and with SwiftPM. Its
  first job, `changes`, runs `scripts/ci-changes.mjs` on the files the change touches: the code jobs run for any file
  but the docs, the native builds for native code, codegen specs, the Showcase's native projects, dependencies and
  the build setup; the docs alone and a release run none of them. A new job takes `needs: changes` and the `if` of its
  kind; a file a job newly depends on goes into the lists of the script.
  `nightly.yml` (03:00 UTC, or by hand) builds the iOS example with CocoaPods frameworks (static, dynamic, React Native
  from source) and an Expo app with the packed library.
- `release.yml` runs on every push to `main`: release-please keeps the release pull request open (the `release` skill),
  and after it is merged publishes to npm through the `npm` environment (trusted publishing, no token).
  `pr-title.yml` checks that a pull request's title is a conventional commit; in the merge queue it passes with no
  steps.
- Every job but `changes` starts with `actions/checkout` and the local `./.github/actions/setup` (Node from `.nvmrc`,
  the Yarn cache, `yarn install --immutable`). A new job does too.
- Third-party actions are pinned to a full commit SHA with the version in a comment
  (`uses: actions/checkout@<sha> # v5.0.0`), never a tag or a branch alone. An update changes both.
- Xcode comes from `XCODE_VERSION` in the job's `env`; only the iOS jobs run on macOS.
- A job runs the same yarn script a local check runs, so a failure reproduces with `scripts/`. A new check is a yarn
  script and a `scripts/` wrapper first, then a step.
- Each workflow keeps `concurrency` with `cancel-in-progress`, so a new push cancels the older run of its ref.
- The issue templates are in `ISSUE_TEMPLATE/`; `/fix #<number>` reads a bug report in that shape.
