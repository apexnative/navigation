---
paths:
  - 'src/index.ts'
  - 'src/api/**'
  - 'src/engine/**'
---

# The public API

- Public is what `src/api/index.ts` exports, and the option types `src/index.ts` adds. `src/engine` is internal and
  never imports `src/api`.
- Every export has JSDoc: `docs/scripts/api.mjs` builds the API reference from it.
- A new, renamed or removed export changes `docs/sections.json`: each export in exactly one section.
  `scripts/docs.sh` must pass.
- Screen names and props are typed through `Register` (module augmentation, `src/api/types.ts`). Layouts are checked
  where they are used (`switchTo`, `setStack`, the roots of `createNavigation`), not in the builders: `Register` in a
  builder's type makes a cycle (TS7022).
- What JS can check, it checks before the native side: an unknown screen name, a repeated id, a screen that is gone.
  Errors read `[navigation] <command>: <reason>`.
- Commands made through `navigation.*` wait for the first root.
- Brands and internal markers are symbols named `apexnative.navigation.*`. Reserved names: the screens
  `ApexNavigation.StartFailure`, `ApexNavigation.Splash` and `apexnative.navigation.buttonTab`; the button ids `CloseButton`,
  `ApexNavigation.back`, `ApexNavigation.hardwareBackButton`.
