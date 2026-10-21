---
paths:
  - 'src/**/*.{ts,tsx}'
  - 'example/src/**/*.{ts,tsx}'
  - 'cli/**/*.js'
  - 'scripts/**/*.mjs'
  - 'docs/src/**/*.{ts,tsx}'
  - 'docs/scripts/**/*.mjs'
---

# TypeScript and JavaScript

Prettier (100 columns, single quotes, semicolons, es5 trailing commas) runs after every edit; ESLint runs in
`scripts/lint.sh` and the pre-commit hook. `tsconfig` is strict, with no unused locals or parameters.

## Names

- Files: PascalCase when the main export is a component, class or builder (`Stack.ts`, `SlotHost.tsx`), camelCase
  otherwise (`createNavigation.ts`). Showcase scenarios: `<n>-<name>/<n.m>-<slug>.ts`.
- Functions: `use*` hooks; `on*` / `subscribe*` return an unsubscribe; `when*` wait for something; `is*` type guards;
  `to*` converters; `remember*` / `forget*` keep and drop entries of a store; `*Now` is the step after a wait.
- Types: no `I` prefix. Public types end in `Event`, `Element`, `Config`, `Handler`, or start with `Navigation`.
  Generic parameters have names (`Name`, `Selected`); `T` only in helpers that pass a type through.
- Constants: `UPPER_SNAKE` for fixed values, camelCase for module state. Private means not exported, no `_`.
- Internal markers: `Symbol('apexnative.navigation.<name>')`.

## Modules

- Named exports. A default export only where a tool needs one: codegen specs, scenarios, lazily loaded screens.
- The public API lists its exports one by one in `src/api/index.ts`, types in `export type { … }`.
- `import type` for what is used only as a type. Packages first, then relative imports.

## Types

- `interface` for object shapes; `type` for unions, mapped and conditional types. No enums.
- `unknown`, not `any`; `any` only as a generic wildcard (`NavigationApp<any, any>`).
- Exported functions declare their return type. `!` and `as` rarely; narrow instead. `as const` and `satisfies` are
  fine.
- A type parameter inferred from a value that may be a callback stands for the result, with the callback spelled out
  (`Layout | ((context: Context) => Layout)`): a parameter for the whole value leaves `(context) => …` untyped.
- A type parameter inferred from an object whose values may be screen names is `const`: without it `'Login'` widens
  to `string`, and the screen check rejects it.

## Comments

- Every export has JSDoc that starts with a noun phrase ("The screen a command acts from…"); a boolean starts with
  "Whether". No `@param` / `@returns`: the prose names the parameters. Platforms as a prefix: `(iOS 16+)`,
  `(Android)`.
- `//` comments on a line of their own, in full sentences, saying why rather than what. No "we", no TODO.

## Errors

- Commands run through `command(name, run)` (`src/api/commands/errors.ts`): `[navigation] <command>: <reason>`,
  the reason in lowercase. Other errors: `[navigation] ` and a sentence. Warnings use the same prefix, in development
  only.
- A command rejects; it doesn't throw synchronously. An error that is ignored: `catch {` without a binding.

## React

- Function components with the props typed in the parameter; a class only for an error boundary.
- External stores through `useSyncExternalStore`; its `subscribe` in `useCallback` when it depends on an id. The
  latest handler lives in a ref (`useRef` + `useLayoutEffect`). An effect returns its unsubscribe.
- `memo(function Name(…) {…})`; generated components get a `displayName`.
- Showcase: one scenario, screen or component per file. A screen is a private component and
  `export const XScreen = defineScreen({ … })`; `StyleSheet.create` at the end; `const t = useTheme()`,
  `const nav = useNavigation()`.

## The CLI and scripts

- `cli/`: every file starts with `// @ts-check` and `'use strict'`, uses `require('node:…')`, ends with
  `module.exports = { … }`. Types as JSDoc `@typedef` / `@param`; shared ones in `types.js`.
- A CLI command returns its exit code (`Promise<number>`); `main` sets `process.exitCode` and never calls
  `process.exit`. An expected failure is data: `run.fail('kebab-code', 'Full sentence.')`. Dependencies (`Exec`, `ui`,
  `out`) are passed in, so tests can replace them.
- `scripts/*.mjs`: a shebang, a header comment with the purpose and `Usage:`, ESM.

## Models

New code looks like these files; read the closest one before writing.

- A hook: `src/api/hooks/usePreview.ts`; one over a store: `src/api/hooks/appHooks.ts`.
- A layout builder: `src/api/layout/Stack.ts`.
- A command: `push` and `pushNow` in `src/api/commands/commands.ts`.
- A screen of the library itself: `src/api/app/startFailure.tsx`.
- A Showcase scenario: `example/src/sections/2-bar-buttons/2.7-overflow.ts`.
- A CLI check: `cli/doctor/checks/ios.js`.
