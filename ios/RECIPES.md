# iOS recipes

The steps for the two changes that touch many files on iOS: adding an option and adding a command. Read it when
you do exactly that; the map is `ios/CLAUDE.md`.

## Adding an option

1. **The type and its JSDoc** in `src/interfaces/Options.ts` (the JSDoc is the reference docs). A new root
   option also goes into the `options` of its section in `docs/sections.json`.
2. **The options class.** The group's `ANN<Group>Options` in `Options/`: declare the field once in `+optionFields`
   (`boolField:`, `colorField:`, `enumField:values:`, `groupField:class:`, …). Parsing, merging, copying and `hasValue`
   come from `ANNOptions`. A new group is a new `ANN<Group>Options` pair, added to its parent's fields with
   `groupField:class:`. The DEBUG check `ANNCheckOptionClassesFrom` walks the field tables once.
3. **The presenter that applies it.** Read the value from the resolved options (`resolveOptionsWithDefault`) in the
   presenter of its area: at the screen's creation, at each appearance, in `mergeOptions:resolvedOptions:` (apply only
   what the merge names) and in `applyDefaultOptionsChange:`. A system-UI option also goes into
   `+[ANNSystemUI groupsNamedBy:onlyRead:]`; a bar-placement option into `ANNOptionsChangeScreenBars`.
4. **The cache.** Never invalidate by hand. Own options change through `ANNNodeHelper` (`mergeOwnOptions:`,
   `changeOwnOptions:`) and defaults through `+[ANNDefaultOptions setCurrent:]`; both call
   `+[ANNOptionsCache optionsChanged]`. A presenter or module that keeps a choice of the user writes it with
   `changeOwnOptions:`, never into the options in place.
5. **Android** reads the same JSON and has unit tests of parsing and merging; the iOS side has none (see Checks in `ios/CLAUDE.md`).
6. **The rule:** an option does what its name promises, with the same meaning on both platforms
   (`.claude/rules/general.md`, `.claude/rules/options.md`). A platform that can't, says so in the JSDoc.

## Adding a command

1. The method in the codegen spec `src/adapters/NativeANNTurboModule.ts`; the engine side in `src/engine/commands.ts`
   (`.claude/rules/native-contract.md`).
2. **`ANNTurboModule`:** the method. Call `+[ANNCommandRunner run:…]` with a `check` block that reads the layout
   (`ANNLayoutReader`) and a `body` block that calls the area class. A command without a promise uses `runVoid:`.
3. **The area class** does the work on the main queue and answers once. Its checks come before any node is made, in the
   same order on both platforms; read the stack as JS sees it (`ANNStackQueue.entries`).
4. **The layout check:** a new node type goes into `ANNLayoutType` and `ANNLayoutReader` (its place, its children), and
   gets a maker in `ANNLayoutFactory`.
5. **Error texts** come from `ANNErrorTexts`, one sentence per case, the same as Android; a text deep in a maker is
   thrown with `+[ANNErrorTexts rejection:]`. A new text goes into `src/interfaces/contract/errors.json`, and
   `node scripts/contract.mjs` makes `ANNErrorTexts` (and Android's `ErrorTexts`) from it; never edit them by hand.
6. **The answer:** end through `ANNAppearanceDispatcher` (`endTransitionHiding:…answer:value:`) or call
   `-[ANNCommandAnswer resolve:]`; the state and `ANN.CommandCompleted` follow by themselves for the names in
   `ANNCompletedCommandNames`. Add the name there when the command shows or dismisses.
7. **Android** gets the same command; its tests check the texts.
