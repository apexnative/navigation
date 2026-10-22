# @apexnative/navigation

Native navigation for React Native on iOS, iPadOS and Android: platform stacks, tabs, modals, side menus, split
views and overlays, owned by the native side and driven from JS. React Native 0.85+ with the New Architecture
(bridgeless, Fabric) only, developed on 0.87; iOS 15.1+, Android minSdk 24.

More rules are in `.claude/rules/`: `general.md` always applies, the others load with the files they cover.

## How it works

```
app code      createNavigation, defineScreen, Stack(…), navigation.push(…), hooks       src/api     (public)
   │ layouts and options
   ▼
src/engine    layouts and options → JSON; an id for every command and every screen    (internal)
   │ TurboModule ANNTurboModule (src/adapters/NativeANNTurboModule.ts)
   ▼
native        the core: iOS Objective-C++, Android Kotlin — ARCHITECTURE.md, ios/CLAUDE.md, android/CLAUDE.md
   │ events ANN.<Name>
   ▼
src/engine/native.ts → src/api/events → hooks and listeners, with the screen as { id, name }
```

- The native side owns the navigation state: stacks, tabs, modals. JS builds a layout tree, turns it into JSON
  (`src/engine/layout.ts`, `src/engine/options.ts`) and sends a command (`src/engine/commands.ts`). Ids look like
  `Component12`, `push13`. The JSON, the commands and the events are the types of `src/interfaces/`; their
  order, threads and errors are `ARCHITECTURE.md` section 5.
- The props of a screen stay in JS by its id (`src/engine/props.ts`); React Native gets only the id. The `onPress`
  handlers of buttons stay in JS too (`src/engine/buttons.ts`).
- A React screen is its own React Native root, or, with `appProvider`, every screen renders in one React tree: a
  hidden surface `apexnative.slots` (`SlotHost`) with an `ANNSlot` per screen, which the native side moves into the
  screen's container (layout name `slot:<Name>`).
- `ANNSlot` and `ANNSafeAreaView` are Fabric components with C++ in `ios/cpp/`; Android builds the same C++
  (`android/src/main/jni/CMakeLists.txt`).
- Options reach native as JSON; how they layer and merge is `ARCHITECTURE.md` section 2.3, what each option does is
  its JSDoc in `src/interfaces/`.
- A command that fails on the native side rejects its promise; it doesn't crash the app.

## Map

- `src/index.ts` — the public exports: `src/api` and the option types of `src/interfaces/Options.ts`.
- `src/api/` — the public API. `src/engine/` — internal: commands, layouts and options as JSON, the screens by name,
  props, places, slots, launch state. `native.ts` is the only way to the native side.
- `src/interfaces/`, `src/adapters/` — the option, layout and event types and their JSDoc (the reference docs), and
  the codegen specs. Names of options, fields, commands and events are the contract and don't change.
- `ios/` — pod and SwiftPM target `ApexNavigation`, Objective-C++; its map is `ios/CLAUDE.md`. `android/` — package
  `dev.apexnative.navigation`, Kotlin; its map is `android/CLAUDE.md`. Each folder also has `CORE.md` (layers, flows,
  the single places, modules) and `RECIPES.md` (adding an option or a command), read on demand. `ARCHITECTURE.md` —
  the native core's layers and seams, iOS and Android side by side (the JavaScript side: `src/ARCHITECTURE.md`). Our
  native modules plug into the core through its module interface (`ARCHITECTURE.md` section 4).
- `example/` — the Showcase app: every feature as a scenario.
- `cli/` — the `apex-navigation` CLI. `docs/` — the Docusaurus site. `scripts/` — builds and checks.

## Working on a task

- Commands: `/feature`, `/api`, `/fix` (`.claude/skills/task-flow/SKILL.md`), `/docs`, `/release`.
- Skills: `native-facts`, `native-dev`, `review-change`, `docs`, `git`. Agents (`.claude/agents/`): `researcher`,
  `ios-dev`, `android-dev`, `reviewer`, `docs-writer`, `qa` (the Showcase on a simulator or emulator), `ci-doctor`
  (a failed GitHub Actions run).

## Rules

- **Third-party copyright headers.** Never remove or change an existing copyright line or license text. When you
  modify a file that carries someone else's header, keep the header and add a `Modified by <name>, <year>` line below
  it. `yarn check:headers` and a Claude Code hook enforce this.
- The other rules: `.claude/rules/general.md`.

## Commands

Builds, tests and checks run through `scripts/`. Each prints one line, `✓ name: OK`, or the errors and the path of
the full log in `.logs/`. A hook blocks the raw commands (`xcodebuild`, `gradlew`, `pod install`, `jest`, `tsc`,
`eslint`); a command no script covers runs with `APEX_RAW=1` in front.

```sh
scripts/check.sh                      # the quick checks: types, lint, Jest, headers, the contract, docs API
node scripts/contract.mjs             # the error texts and event keys of both platforms, after a change of their source
scripts/typecheck.sh                  # TypeScript: the library and the Showcase
scripts/lint.sh [files...]            # ESLint + Prettier
scripts/test-js.sh [jest args...]     # Jest (the CLI)
scripts/test-android.sh [--tests …]   # unit tests of the library (JUnit on the JVM)
scripts/build-ios.sh [UDID]           # the Showcase for the simulator; with a UDID: install and start it there
scripts/build-android.sh [--lib | SERIAL]  # the Showcase (arm64); --lib: the library only; SERIAL: install, start
scripts/pods.sh                       # pod install, after a native or spec change on iOS
scripts/docs.sh [api | typecheck | build]  # the docs site
```
