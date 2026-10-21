---
name: release
description: How @apexnative/navigation is released — the release pull request of release-please, the npm approval, moving between alpha, beta, rc and stable — and the check of a release pull request before the maintainer merges it. Never publishes.
disable-model-invocation: true
argument-hint: [check | beta | rc | stable]
---

# A release

A release is a pull request. After every merge into `main`, `.github/workflows/release.yml` (release-please) keeps one
pull request open, `chore: release <version>`: the version in `package.json` and `.release-please-manifest.json`, and
`CHANGELOG.md` from the conventional commits since the last release. The maintainer merges it through the merge queue;
the workflow then tags `v<version>`, makes the GitHub release, and publishes to npm under the stage's tag (`alpha`,
`beta`, `rc`, or `latest`) once the maintainer approves the `npm` environment. npm trusts the workflow (trusted
publishing): no npm token anywhere. Claude never merges the release pull request and never approves the environment.

## check (the default)

Before the maintainer merges the release pull request:

1. **CI** is green on it, and on the last commit of `main` (`gh run list --branch main --limit 3`).
2. **Checks** here: `scripts/check.sh`, `scripts/build-ios.sh`, `scripts/build-android.sh --lib`,
   `scripts/docs.sh build`.
3. **The changelog** of the pull request: every user-facing change is there, in plain words, under the right heading;
   a breaking change is under `⚠ BREAKING CHANGES`. A wrong entry comes from a pull request's title: say which, and
   the maintainer edits the release pull request's text.
4. **Hand over:** what was checked, the version, and what to look at before merging.

## beta | rc | stable: the next stage

The stage is `prerelease-type` in `release-please-config.json`; the jump is a commit with a `Release-As:` footer.

- **beta:** `prerelease-type` → `beta`, and a commit (`chore: start the beta`) with `Release-As: 1.0.0-beta.0`.
- **rc:** the same with `rc` and `1.0.0-rc.0`.
- **stable:** `prerelease` → `false`, `versioning` removed, and `Release-As: 1.0.0`. The docs lose their pre-release
  banner and the home page shows "Stable" by themselves (the `docs` skill, "Versions and the banner"); the "Before
  1.0" note of `docs/content/start/introduction.mdx` and the status line of `README.md` go in the same pull request.
  From then on `latest` on npm is the stable version: `release.yml` moves it with pre-releases only until then.
- **A new major after a stable release:** keep the released docs first (the `docs` skill), then `prerelease` back to
  `true`, `versioning: prerelease` and `prerelease-type: alpha` back, and `Release-As: 2.0.0-alpha.0`. `latest` on npm
  stays on the stable major; the new one goes out under `alpha`, `beta`, `rc` until its stable release. Fixes of the
  old major while the new one is a pre-release need a branch of their own (`1.x`) with release-please on it, which
  `release.yml` doesn't have yet.

The change goes through a pull request like any other; the release pull request then shows the new version.
