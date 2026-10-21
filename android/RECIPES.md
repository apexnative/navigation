# Android core: recipes

The steps for adding an option and for adding a command to the Android core; read it when doing exactly that.

## Adding an option

1. The type and its JSDoc in `src/interfaces/Options.ts` (`.claude/rules/options.md`). A name that ends in `color` or
   `icon` is converted by `src/engine/options.ts`.
2. The Kotlin group in `options/`: a field of a value type (`Bool`, `Text`, `Fraction`, `Variant`, `ThemeColour`), its
   `parse` (use `parsers/`; a value of the wrong kind stays unset), `mergeWith`, `mergeWithDefault`, `copy` and
   `hasValue`. A new group also needs a field in `Options` and a line in its `parse`, `mergeWith` and `withDefaultOptions`.
   An option that acts once is cleared in `Options.clearOneShotOptions`.
3. The presenter of the area applies it when the screen shows and again on `mergeOptions` and `onConfigurationChanged`
   (a repaint after a light/dark switch or `setDefaultOptions`); a system-bar option goes through `SystemUiRules`.
   Read colors with `ThemeColour.get` at apply so they follow the theme.
4. The cache needs nothing: own options are written only through `ViewController.options`, `changeOptions` and
   `mergeOptions`, defaults only through `DefaultOptions.set`. Never change a resolved result.
5. Tests: add the field to `OptionsFixture.full()`. `WholeTreeTest` walks the tree by reflection and fails when a field
   is not read, copied, merged or defaulted. Add cases to `ReadersTest`, `MergeRuleTest` and the area's rules test.
6. The docs page and a Showcase control; one meaning on both platforms, and where Android can't do it the JSDoc says so
   (`.claude/rules/general.md`).

## Adding a command

1. The method in the codegen spec `src/adapters/NativeANNTurboModule.ts` and the engine in `src/engine/commands.ts`
   (`.claude/rules/native-contract.md`). The Gradle build regenerates `NativeANNTurboModuleSpec`.
2. The override in `NavigationTurboModule`: `runner.run(name, commandId, what, promise, check) { node, answer -> … }`
   for a command with a promise (the `check` reads the layout with `LayoutReader`), `runner.runVoid` without one.
3. The body goes to the owner of the area: `Navigator` (root, modals, overlays), `StackCommands` (stacks), a
   coordinator. A stack operation is a `StackQueue` operation.
4. New layout fields are read and checked in `LayoutReader`; checks against what shows are in `LayoutChecks` or in the
   command, before any node is made. Every rejection text is a function in `ErrorTexts` (same words as iOS).
5. The answer goes through `CommandAnswer`: add the name to `COMPLETED_COMMANDS` when the command changes what shows
   (then the state, the promise and `CommandCompleted` follow in that order). A transition ends through
   `AppearanceDispatcher.endTransition`.
6. Tests: `CommandRunnerTest`, `CommandAnswerTest`, `ErrorTextsTest`, `LayoutReaderTest`; a stack operation also needs a
   case in the queue tests (`StackQueueHarness`).
7. A new event: `EventEmitter` (`emit…`), the payload type in `src/interfaces`, and for an appearance event the
   `AppearanceDispatcher.Sender`.
