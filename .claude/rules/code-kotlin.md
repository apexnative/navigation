---
paths:
  - 'android/**/*.{kt,java}'
  - 'example/android/**/*.{kt,java}'
---

# Kotlin

All library code is Kotlin; no Java. No formatter runs on these files; keep the style by hand.

## Format

- 4 spaces, no tabs (the Showcase's own Kotlin: 2). Lines up to 120 columns. K&R braces.
- Continuation indent 4; trailing commas in multi-line parameter lists. Expression bodies for one-liners; one-line
  guards (`if (x) return`, `?: return`).
- `when` for branches of more than two ways. Imports sorted; another library's `R` gets an alias (`AppCompatR`,
  `MaterialR`).

## Names

- Packages are single lowercase words (`splitview`). One public type per file, named after it; small related types
  may share a file; top-level extensions in `<Topic>Extensions.kt`.
- Constants: `const val UPPER_SNAKE` in a (private) `companion object`.
- The `ANN` prefix only on names JS sees (view managers, events).

## Kotlin

- `val` unless it changes. No `!!` in new code: `?.let`, `?: return`, or `requireNotNull(x) { "why" }`. `lateinit`
  only for what a lifecycle callback sets up.
- `object` for singletons; Kotlin function types for callbacks; `@JvmOverloads` on view constructors.
- `internal` for what only the library uses; `@RestrictTo(LIBRARY)` on public classes that apps must not use.

## Android

- Views only, no Fragments. Material 3 components; colors from the theme (`MaterialColors`).
- Custom views get `@SuppressLint("ViewConstructor")`; `@SuppressLint("RestrictedApi")` only with a comment that says
  why.
- Main thread: `UiThread.post`, or `view.post` to wait for the view's own layout; thread rules in prose ("Main thread
  only.").
- Insets through `ViewCompat.setOnApplyWindowInsetsListener`. Colors are applied again in `onConfigurationChanged`: a
  light/dark switch doesn't recreate the activity.


## Comments

- KDoc on public members, `//` on private ones; full sentences, why rather than what; `[name]` links and backticked
  option paths, no `@param` / `@return`. No "we", no TODO.

## Errors and logs

- A command fails by rejecting its promise with a lowercase reason ("the screen 'X' is not in a stack"); JS adds
  `[navigation] <command>:`.
- Log tag: `ApexNavigation`.

## Models

Our modules that stay show the house style (paths under `android/src/main/java/dev/apexnative/navigation/`); read the
closest one before writing.

- A container module: `viewcontrollers/splitview/SplitViewController.kt`.
- Options of a module: `options/SheetOptions.kt`.
- A custom view: `safearea/SafeAreaView.kt`.
- A singleton with Material components: `views/stack/topbar/titlebar/ButtonMenus.kt`.
