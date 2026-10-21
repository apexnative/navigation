---
paths:
  - 'src/interfaces/Options.ts'
  - 'src/engine/options.ts'
  - 'ios/**'
  - 'android/src/main/**'
---

# Options

An option is one path of `Options` (`src/interfaces/Options.ts`); both platforms read it from the same JSON. What
every option does, its default and its platforms: its JSDoc there; layering, merging and value types:
`ARCHITECTURE.md` section 2.3.

## Adding or changing an option

1. **The type and its JSDoc** in `src/interfaces/Options.ts`. The JSDoc is the reference docs: what the option does,
   its default, and the platforms when it isn't both (`iOS 26+`, `Android`). Write it in plain words.
2. **Names the engine converts** (`src/engine/options.ts`): `color` and `*Color` → a platform color; `icon`, `image`,
   `*Icon`, `*Image` → a resolved image (an `{ ios, android }` icon too); `component` and `resultsComponent` → a React
   component, its props kept in JS; arrays named `*Buttons` → buttons, their props and `onPress` kept in JS. Other
   values go as they are.
3. **Both platforms** read it, merge it as `ARCHITECTURE.md` section 2.3 says, and apply it both when the screen shows and
   when its options change while it shows (and again on a light/dark switch where it holds a color). The native steps:
   `ios/RECIPES.md`, `android/RECIPES.md`.
4. **The Showcase:** a control in a scenario (`example/src/sections/…`); the key of the control is the option's path.
5. **Removing or renaming:** the type, both platforms, the Showcase, and the docs pages that mention it.
