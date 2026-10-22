---
paths:
  - 'src/adapters/**'
  - 'src/engine/native.ts'
  - 'src/engine/commands.ts'
  - 'src/engine/layout.ts'
  - 'src/api/commands/**'
  - 'src/api/events/**'
  - 'src/interfaces/Events.ts'
  - 'src/interfaces/ComponentEvents.ts'
  - 'ios/**'
  - 'android/src/main/**'
---

# The native contract: commands and events

JS and native talk through the TurboModule `ANNTurboModule` (`src/adapters/NativeANNTurboModule.ts`) and the events
`ANN.<Name>`. Both platforms implement the same contract: the types of `src/interfaces/` (methods, the layout JSON, every
event) and `ARCHITECTURE.md` section 5 (ids, ordering, promises, errors, the order of events).

## A layout node

`{ id, type, data: { name, options, passProps?, columns?, key?, functions?, style?, axes?, fraction?, primaryEdge?,
compact? }, children }`, built by `toNativeLayout` (`src/engine/layout.ts`). Types: `Component`, `ExternalComponent`,
`Stack`, `BottomTabs`, `SideMenuRoot` / `SideMenuCenter` / `SideMenuLeft` / `SideMenuRight`, `SplitView`,
`Arrangement`. Every field and nesting rule: the layout types of `src/interfaces/` and the layout check
(`ANNLayoutReader`, `LayoutReader`).

## Adding a command

1. The method in the codegen spec, `src/adapters/NativeANNTurboModule.ts`.
2. `src/engine/commands.ts`: build the JSON, give the command its id (`nextId`), note where screens go (`places.ts`).
3. `src/api/commands/`: the public command; `command(name, …)` (`errors.ts`) wraps it, `placeFor` checks what JS
   already knows before it asks the native side.
4. The native side on both platforms, as `ARCHITECTURE.md` describes the path of a command; the steps are in
   `ios/RECIPES.md` and `android/RECIPES.md`.
5. Codegen runs again after a spec change: `scripts/pods.sh` for iOS, a Gradle build for Android.

## Errors

A command that can't be made rejects with `[navigation] <command>: <reason>`, the same text on iOS and Android. What
JS knows itself (the screen is gone, not in a stack, nothing to go back to) it tells before the native side is asked.
The native reasons are one table, `src/interfaces/contract/errors.json`: a new text goes there, and
`node scripts/contract.mjs` makes `ANNErrorTexts` (iOS) and `ErrorTexts.kt` (Android) from it. Never edit the made
files; `scripts/check.sh` fails when they differ from the table.

## Adding an event

1. The payload type `<Name>Event` in `src/interfaces/ComponentEvents.ts` or `Events.ts`; a JSDoc that starts with
   `(iOS…)` makes it iOS only. Then `node scripts/contract.mjs`: it makes the name and the keys of the payload for both
   platforms (`ANNEventContract`, `EventContract`) and the list of events the iOS emitter supports.
2. The native side on both platforms, its name and keys from those files, never a string.
3. The name in `NATIVE_EVENTS` (`src/api/events/navigationEvents.ts`) and the public event type there; screen
   events reach the app with the screen as `{ id, name }` (`src/engine/events.ts`).
4. The hook or listener that reads it (`src/api/hooks/screenHooks.ts`).
