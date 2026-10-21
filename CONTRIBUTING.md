# Contributing

Contributions are always welcome, no matter how large or small. Please read the
[code of conduct](./CODE_OF_CONDUCT.md) before contributing.

## Environment

| Tool        | Version                                   | Notes                                                                          |
| ----------- | ----------------------------------------- | ------------------------------------------------------------------------------ |
| Node.js     | see [`.nvmrc`](./.nvmrc) (>= 22.13)       |                                                                                |
| Yarn        | pinned in [`.yarnrc.yml`](./.yarnrc.yml)  | run `yarn` from the repository root; npm is not supported                      |
| Xcode       | 26 or newer                               | iOS and iPadOS; the library builds with the iOS 26 SDK                         |
| Ruby        | see [`.ruby-version`](./.ruby-version)    | Bundler installs CocoaPods into `example/vendor/bundle`                        |
| JDK         | 17                                        | selected per command by `scripts/android-env.sh`; your global Java stays as is |
| Android SDK | platform 37, build-tools 37.0.0, NDK 27.1 | `ANDROID_HOME`, defaults to `~/Library/Android/sdk`                            |

Check your setup:

```sh
yarn doctor          # or: yarn doctor --json
```

It checks the repository's tools (Node, Yarn and the installed dependencies, Ruby, Bundler and the gems), then
the Showcase the way `npx apex-navigation doctor` checks any app: its native files, JDK 17, the Android SDK,
Xcode, CocoaPods and up-to-date pods (run `pod install` after changing native code or the codegen specs), and the
simulators and devices to run it on. Each problem comes with its fix; it exits with 1 when it finds one.

## Development workflow

The repository contains the library in the root directory and the Showcase app in
`example/`, connected with [Yarn workspaces](https://yarnpkg.com/features/workspaces).

```sh
yarn                  # install dependencies
yarn example start    # start Metro
yarn example ios      # run the Showcase app on iOS
yarn example android  # run the Showcase app on Android
```

The Showcase app uses the library from source: JavaScript changes show up without a rebuild,
native changes need one. To work on native code, open `example/ios/Showcase.xcworkspace` in
Xcode (sources under `Pods > Development Pods > ApexNavigation`) or `example/android` in
Android Studio.

Before sending a change:

```sh
yarn typecheck
yarn lint             # yarn lint --fix to fix formatting
yarn test
yarn check:headers
```

## Copyright headers

Some files carry copyright headers of their original authors. Never remove or change them.
When you modify such a file, keep the header and add a `Modified by <name>, <year>` line below
it. `yarn check:headers` (also run by CI and the pre-commit hook) verifies this for the files
listed in `scripts/license-headers.json`.

## Commit message convention

We follow the [conventional commits specification](https://www.conventionalcommits.org/en):

- `fix`: bug fixes, e.g. fix a crash when a screen is dismissed twice.
- `feat`: new features, e.g. add a new option.
- `refactor`: code refactoring without behavior changes.
- `docs`: documentation changes.
- `test`: adding or updating tests.
- `chore`: tooling changes, e.g. CI configuration.

Keep one behavior change per commit. The commit-msg hook checks the format.

## Publishing to npm

We use [release-it](https://github.com/release-it/release-it) to bump the version, tag and
publish:

```sh
yarn release
```

## Sending a pull request

- Prefer small pull requests focused on one change.
- Make sure linters, tests and the header check pass.
- For changes to the API or implementation, open an issue to discuss first.
