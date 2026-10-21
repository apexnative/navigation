---
paths:
  - 'cli/**'
---

# The CLI (`apex-navigation`)

- `cli/index.js` is the entry (bin). CommonJS with `// @ts-check`.
- Commands, one folder each: `init/`, `setup/` (`steps/`: appDelegate, infoPlist, entry, app, mainActivity,
  mainApplication, manifest, styles), `screen/` (`templates/`: react, native, swiftui, uikit, compose, views),
  `codegen/` (Swift and Kotlin registration of native screens, `commands.js`: their typed commands), `links/` (the
  native declarations of `linking`, `open`, the web files), `icons/` and `splash/` (images with the app's `sharp`,
  the iOS and Android files), `migrate/` (native rewrites, the entry point, the report), `upgrade/`, `info/`,
  `mcp/` (the server, its tools, the docs for agents), `doctor/` (`checks/`, `fix.js`).
- Shared modules next to them: `app.js` (the React Native versions: `MINIMUM`, `TESTED`), `config.js` (the
  `apexNavigation` key in the app's `package.json`), `changes.js` (with `afterWrite`), `diff.js`, `exec.js`,
  `gradle.js`, `images.js` (sharp from the app), `pods.js`, `project.js`, `typescript.js`, `ui.js`, `workspace.js`,
  `xcode/pbxproj.js`.
- Every command that changes files shows the changes first and asks; `--yes` applies them, `--dry-run` writes
  nothing, `--json` prints one JSON document (for CI and agents). Exit code 0 or 1. Under `mcp` the commands run as
  tools: their stdout is the protocol's, so a command never prints outside its JSON and never runs a program with
  the terminal's output under `--json`.
- Tests: `cli/__tests__/` and `cli/*/__tests__/`, run by `yarn test`. Fixtures: `cli/__fixtures__/app-0.87/` and
  `cli/setup/__fixtures__/` (React Native templates); ESLint skips them.
