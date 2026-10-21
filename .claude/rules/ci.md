---
paths:
  - '.github/**'
---

# GitHub Actions

- `ci.yml` runs on pushes and pull requests to `main` and in the merge queue: lint (ESLint, `tsc`, headers), Jest, the
  package build (`yarn prepare`), the Android example, the iOS example with static libraries and with SwiftPM.
  `nightly.yml` (03:00 UTC, or by hand) builds the iOS example with CocoaPods frameworks (static, dynamic, React Native
  from source) and an Expo app with the packed library.
- Every job starts with `actions/checkout` and the local `./.github/actions/setup` (Node from `.nvmrc`, the Yarn cache,
  `yarn install --immutable`). A new job does too.
- Third-party actions are pinned to a full commit SHA with the version in a comment
  (`uses: actions/checkout@<sha> # v5.0.0`), never a tag or a branch alone. An update changes both.
- Xcode comes from `XCODE_VERSION` in the job's `env`; only the iOS jobs run on macOS.
- A job runs the same yarn script a local check runs, so a failure reproduces with `scripts/`. A new check is a yarn
  script and a `scripts/` wrapper first, then a step.
- Each workflow keeps `concurrency` with `cancel-in-progress`, so a new push cancels the older run of its ref.
- The issue templates are in `ISSUE_TEMPLATE/`; `/fix #<number>` reads a bug report in that shape.
