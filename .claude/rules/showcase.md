---
paths:
  - 'example/**'
---

# The Showcase

`example/README.md` explains the layout and how to add a scenario.

- Every feature is a scenario: `example/src/sections/<n>-<name>/<n.m>-<slug>.ts`, made with `defineScenario`
  (`sections/scenario.ts`) and listed in its section's `index.ts`. `check` says what to look at.
- A control's key is an option's path (`'topBar.subtitle.color'`); unset, the option isn't sent
  (`showcase/controls/optionsCode.ts`). `runs` are buttons for what controls don't cover.
- `babel/scenario-source.js` shows the source of each `runs` button in the app: write it as an app would.
- One scenario, screen or component per file. UI comes from `example/src/ui/` (components, theme, `icons.ts`).
- The app itself: `src/app/` (`App.ts` with `createNavigation`, `screens.ts`, `roots.ts`, `options.ts`,
  `listeners.ts` into the Journal). Screens the scenarios show: `src/demo/`; the Examples tab: `src/examples/`.
- Native screens: `example/ios/Showcase/NativeScreens/` (registered in `AppDelegate.swift`) and
  `example/android/app/src/main/java/**/nativescreens/` (registered in `MainApplication.kt`).
- The Showcase uses the library from source (Metro condition `apexnative-navigation-source`, `tsconfig` paths): a JS
  change reloads through Metro, which every connected app shares; a native change needs a rebuild.
