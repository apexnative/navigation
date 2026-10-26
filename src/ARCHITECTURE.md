# JavaScript Architecture

The JavaScript side builds what the app asks for — roots, screens, options — into the JSON the native side reads, and
turns the native side's events into hooks and handlers. The native side owns the navigation: its stacks, tabs, modals
and their state.

```
app code ─ createNavigation, defineScreen, Stack(…), navigation.push(…), hooks       src/api     (public)
               │ a layout tree (Layout) and the options of the show, in layers
               ▼
src/engine     commands.ts      each command: wait for lazy screens, build the JSON, send it with an id of its own
               layout.ts        ids → own options → button handlers → native screen, or key and props → slot → JSON nodes
               options.ts       colors per platform, images, components in options → JSON options
               nativeScreens.ts native screens: data props as JSON, functions kept here, `useProps` in the hidden host
               │
               ▼
native.ts      commands ─► TurboModule `ANNTurboModule` ─► native side
               events   ◄─ `ANN.*` events             ◄─ native side
               │
               ▼
events.ts      an event of a screen as the app gets it: the screen as { id, name }
```

`src/index.ts` is `export * from './api'` (what `src/api/index.ts` exports) and `export * from './interfaces/Options'`
(the types of options). The layout and event types of `src/interfaces` stay internal: the app gets its events in the
form of `src/api/events/navigationEvents.ts`. Everything in `src/engine` is internal, and it never imports `src/api`.

## `src/api` — the public API

- `navigation.ts` — `navigation`, the commands for anywhere: they act from the screen the user sees and wait for the
  first root (all but the `dismiss…` ones); also `getPendingLink`, `openPendingLink`, `clearPendingLink`.
  `types.ts` — `Register` and the types that check screen names and props. `testIDs.ts` — `TestIDs`.
- `app/` — `createNavigation` (the app: screens, roots, theme, providers, the start) and `AppRegistry`, which starts it
  on every `AppLaunched`. `activeApp.ts`: the started app's roots, `presentation` and `mounted`, for the commands made
  outside it. `roots.ts`: `resolveRoot`, a root of `roots` by name and param. `startFailure.tsx`: the screen a failed
  start shows. `splash.ts`: `splash` of `createNavigation` and `useSplash` — whether it shows (`showsSplash`: a cold
  start by `getStartInfo`), its root `ApexNavigation.Splash` (`SPLASH_ROOT`: at once, the whole window, waiting for
  its render), and when it may leave (`showSplash`: `minDuration`, `hide()`).
- `layout/` — the builders: `Root`, `Stack`, `Tabs`/`Tab`, `SideMenu`, `SplitView`, `Screen`. Each builds a `Layout`
  (`src/interfaces/Layout.ts`) when a command takes it (`[BUILD]`, `element.ts`).
- `commands/` — `commands.ts`: what `navigation` and `useNavigation` do, with the options of a show in layers.
  `presentation.ts`: the configs of a push, a modal, an overlay and a column, and the app's `presentation`.
  `transitions.ts`: a `Transition` as `animations` options. `sideMenus.ts`: `openMenu`, `closeMenu`, and the center a
  menu screen acts from. `errors.ts`: `command()`, which also counts the commands on their way (`trackCommand`, also
  for `selectTab` and `setTabBadge`; `whenCommandsSettle(since)` resolves once those started after a moment settled,
  at most after 3 s), and `placeFor()`.
- `screens/` — `defineScreen`, `nativeScreen`, `defineArrangement` and `arrangement` of a screen (`arrangement.ts`: the
  pair with its settings at their defaults, `toEngineArrangement`), and `registerScreens`, which wraps every React
  screen and registers it
  with the engine: `ScreenContext`, `ANNSafeAreaView` (the content renders once it is laid out; a screen the native
  side laid out before its push renders it at once, the view starting with that padding, `initialPadding`),
  `screenProvider` between two `ScreenBoundary`s (one for the provider, one inside it, so `errorFallback` has what it
  provides), `Suspense` with `loadingFallback`. A native screen's `useProps` gets the context, the provider and a
  boundary too. `ScreenBoundary.tsx`: an error of a screen stays in it. `nativeScreenCalls.ts`: runs what native
  screens call — the functions of their props, and the commands of their navigation.
- `hooks/` — `useNavigation.ts`: `useNavigation` and `screenNavigation`, the commands from a screen (an overlay acts
  from the screen the user sees, a menu screen from the center of its side menu). `screenHooks.ts`: `useScreen`,
  `useScreenEvent`, `usePreventLeave`, `useScreenOptions`, `useNavigationState`, `useTabPress` and the rest.
  `appHooks.ts`: `useNavigationTheme`, `useAppContext`. `useSafeAreaInsets.ts`, `useReservedRegions.ts`,
  `useArrangement.ts`, `useHinge.ts` (`useHinge`, `useHingeChange`). `usePreview.ts` (see Previews).
  `usePendingLink.ts` (see Links).
- `linking/` — `linking` of `createNavigation` (see Links). `types.ts`: the public types (`LinkingConfig`, `LinkRoute`,
  `LinkParams`, `PendingLink`, …). `linkPaths.ts`: a URL into its path and query (`parseLink`), the patterns ordered
  (`compileRoutes`) and matched (`matchLink`). `linking.ts`: the links of the app — the launch link, the `url` events
  of React Native's `Linking`, the routes run, the pending link opened after `setContext`. `pendingLinks.ts`: the one
  waiting link and its listeners, which `navigation` and `usePendingLink` read. `dispatchedLinks.ts` (iOS): hands
  `Linking` the links of `ANN.DeepLinkDispatched`.
- `events/` — `navigationEvents.ts`: the events by name, the native event behind each, `onNavigationEvent`,
  `listeners`. `screenLifecycle.ts`: what goes when a screen unmounts, `whenScreenUnmounts`. `tabPresses.ts`: the
  handlers of `useTabPress`.
- `options/` — `OptionsChain.ts`: the modifiers of options. `ScreenOptions.ts`: the chain of a screen's options,
  `readScreenOptions`. `buttons.ts`: buttons, menus and the FAB with `onPress`.
- `state/navigationState.ts` — the last `NavigationStateChanged`: the screen the user sees and the selected tab.
  `theme/theme.ts` — the theme, the default options made from it, `setTheme`, `setDirection`. `context/appContext.ts`
  — the app's context.
- `safeArea/` — `ANNSafeAreaView`, the insets of each screen (`screenInsets.ts`) and its reserved regions
  (`screenRegions.ts`). `arrangement/arrangementState.ts` — the state of each screen of a pair. `hinge/hinge.ts` — the
  device's hinge, watched while something listens (see Pairs, regions and the hinge). `slots/` — `ANNSlot` and
  `SlotHost`, the app's hidden React root: with `appProvider`, the provider around a slot for every React screen
  shown; in any mode, the `useProps` hooks of the native screens. Without `appProvider` every React screen is a React
  root of its own. A held slot renders nothing; a slot with a frame renders `ANNSlot` at that size (`initialSize`)
  and, in a layout effect, tells the engine React committed it (`slotCommitted`).
- `src/adapters/` — the codegen specs: `NativeANNTurboModule.ts` (the commands) and `NativeANNTurboEventEmitter.ts`
  (the module iOS sends its events through).

## `src/engine` — the engine

| Module                      | What it does                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `native.ts`                 | `commands`: the TurboModule (`src/adapters/NativeANNTurboModule.ts`). `onNativeEvent(name, listener)`: one native event, `ANN.<name>`. iOS sends events through `ANNTurboEventEmitter`; Android without a module. `getLaunchLink()`: the URL that launched the native side, null without the native side (Jest). `getStartInfo()`: `{ coldStart, splashLogo, arrangements }`, asked once; without the native side a cold start without a logo and without arrangements.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `layout.ts`                 | `toNativeLayout(layout)`: a `LayoutNode`, `{ id, type, data: { name, options, passProps?, functions?, key?, columns? }, children }`. For a screen, in this order: a name that shows a pair becomes an `Arrangement` node (see Pairs, regions and the hinge), an unknown name fails, its id, its own options under those of the show, the press handlers of its buttons taken out; then a native screen goes as an external component (`showNativeScreen`), a React screen with its `key`, its props kept here, as a slot in one tree.                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `options.ts`                | `toNativeOptions(options, owner)`: colors (iOS: a number, `{ light, dark }` or `'NoColor'`; Android: always `{ light, dark }`), images (`require` resolved), components in options (`componentId`, their props kept here; without an `id` of their own, `<owner>/<place>/<name>`, e.g. `Component12/topBar.title/Clock`, a list item by its id where it has one (`topBar.rightButtons[Save]`), the same on every `mergeOptions` of the node, so the native side keeps the mounted root), the props of buttons with a component, the roots of the bar buttons' components (`*Buttons`, the toolbar's `buttons`) remembered. Options come in their own form only.                                                                                                                                                                                                                                                                                                            |
| `commands.ts`               | `setRoot`, `push`, `pop`, `popTo`, `popToRoot`, `setStackRoot`, `showInColumn`, `setColumnShown`, `showModal`, `dismissModal`, `dismissAllModals`, `showOverlay`, `dismissOverlay`, `dismissAllOverlays`, `mergeOptions`, `setDefaultOptions`. One that shows screens waits for lazy ones (`afterLoading`), checks the ids (`checkIds`), notes the places and waits for the hooks of native screens (`whenNativeScreensReady`). On Android, in one tree, a push goes through `preparePush` first (see One command).                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `screens.tsx`               | The screens by name: a React component with its options, `key`, `load` (a lazy screen) and `arrangement` (its pair, if it has a secondary), a native screen (`nativeName`, the controller of its `useProps`, `arrangement`), or a pair entry of `defineArrangement` (`pair`). `arrangementOf(name)`: the pair a name shows; `checkArrangements` at registration, `checkArrangement` when a lazy screen's pair shows. `loadScreens`/`afterLoading`: a command goes once its lazy screens loaded, at once when none waits. A React screen is registered with `AppRegistry` as a root (`screenRoot`) that renders it with the props left for its id. After a JavaScript reload React Native starts the old roots again under the new screens' ids: a root that unmounts while another root of the id is mounted leaves the screen alone. The props of a component the library named in options go with its node, not with a root (several roots may show one default). |
| `nativeScreens.ts`          | The native screens shown, by id. Data props go as JSON (`passProps`); functions stay here, their names in `data.functions`, and the native side calls them (`ANN.NativeScreenCall`, answered with `resolveNativeScreenCall`). With `useProps`: the hidden host runs the hook (`startHost`, 5 s to start), the command waits for its first result, later changes go with `updateNativeScreenProps`. `ANN.NativeScreenReleased` forgets one.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `props.ts`                  | The props of screens and of components in options, by id: a command puts them here before the native side creates the view; React Native passes a root only its id. Also which roots are the components of bar buttons (`isButtonRoot`): on iOS their wrapper centers the content in the slot.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `events.ts`                 | `toScreenEvent(event)`: an event of a screen as `listeners`, `navigation.on`, the screen hooks and `onPress` get it: the screen as `screen: { id, name }`, without the fields of the native side.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `places.ts`                 | Where each screen is, noted as the commands build their layouts: the root, a modal or an overlay, its stack and its index there, its side menu, its pair (`arrangement`: the pair's id and its placement; `alone` for a primary shown without one, `noteAlone`). The column of a split view each stack shows (`columnOf`, `stackOfColumn`), and whether a split view's columns show as one stack (`ANN.SplitViewColumnsStacked`, `listenToSplitViews`): `showInColumn` notes its screen first in the column's stack, or over the screen that asks in the compact column, and the first screen of a column over the one before it can pop (`coversColumn`). `canPop`, `canDismiss`, `dismiss` and `placeFor` read it at once; `isGone` knows the last screens that went. It also notes the layouts each screen shows in (`layoutsOf`): a layout whose last screen went is gone, and so are the props of the components named in its options.                         |
| `buttons.ts`                | The `onPress` handlers of the buttons of each screen and of their menu items, called on `NavigationButtonPressed`; the API gives the event its form (`setPressEvent`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `tabs.ts`                   | The press handlers of `Tab().onPress()`: they come with the tabs' layout, stay under the id the tabs are shown with, and go once every React screen shown in the tabs (pushed ones too) unmounted.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `slots.ts`                  | One tree (`appProvider`): `slotName` sends a React screen as `slot:<name>`, the native side gives it an empty container, and the slot with its id in `SlotHost` renders it. `withSlots` takes back the slots of a failed command; `ANN.SlotDestroyed` removes one. For a push on Android: `holdSlot` (nothing renders until the native side laid the screen out), `showSlot` (at its `SlotFrame`; resolves once React committed it, `slotCommitted`), `preparedPadding` (the first padding of its safe area view).                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `launchState.ts`            | The last root and default options: Android destroys the activity while JavaScript lives on and sends `AppLaunched` again; the app sets them again.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `backListeners.ts`          | Android: wraps `BackHandler.addEventListener` at the launch to count the listeners, and tells the native side whether there is one (`setBackListeners`): the navigation takes the back button only while it can go back or JavaScript listens. A listener added before the launch isn't counted.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `reactMeasures.ts`          | Development only: every command that shows or closes screens drops React's `performance.measure` entries of component renders (named after the component with U+200B in front). React Native keeps them without a limit, and a long session ran out of memory; the app's own measures stay.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `ids.ts`, `mergeOptions.ts` | `nextId` and the merge of options; see Ids and Options in layers.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

## The start of the app

1. `index.js` calls `AppRegistry.registerComponent(name, app)`, or passes a function that returns the app, so its
   module loads only at launch. It registers `SlotHost` as `apexnative.slots` and listens to `ANN.AppLaunched`.
   `createNavigation` itself, with `linking`, starts the links (`setupLinking`): from then on a `url` event of
   React Native's `Linking` waits for the first root.
2. The native side sends `AppLaunched` on every launch: the first one, after a JavaScript reload, and on Android with
   each new activity. `startApp` runs the app's launch.
3. Once per JavaScript runtime the launch sets the theme and the app's defaults (`initTheme`), the context, the slots
   (`enableSlots(appProvider)`) and the listeners: navigation state, side menus, native screens and their calls,
   the `BackHandler` listeners (`watchBackListeners`, Android), `listeners`. On every launch it sends the default
   options (the last ones set, else `themedDefaultOptions()`).
4. The first launch mounts: `registerScreens` (the screens, the groups, and `ApexNavigation.StartFailure` when there is
   an `errorFallback`), with `linking` the launch link asked for (`launchLink()`, before any React content appears:
   on iOS that would hand the link to `Linking` as a `url` event), `startHost()` when a native screen has `useProps`,
   with `splash` on a cold start (`getStartInfo().coldStart`, at the first start of the runtime only) the splash's
   root set at once while the rest runs, `willMount` (once per runtime), the first root through `setRoot` — the launch
   link's `start` when its route has one and `when` lets it (`rootFromLink`), else `root` or `initialRoot(context)` of
   `roots`; after the splash, once it may leave (`minDuration`, `hide()` with `'manual'`), with `splash.transition`
   unless the root has a setRoot animation of its own (`withDefaultTransition`) — `restoreRoot()` in case the
   activity changed meanwhile (a root Android answers without an id, its activity gone before it showed, waits for the
   next launch and is set there; a splash answered so waits to be released), then `mounted` resolves (the commands of `navigation` wait for it), `didMount(context)`
   runs, and the links start (`linksStarted`, see Links). The splash component is registered as
   `ApexNavigation.Splash`, a React root of its own (`ownRoot`) without `screenProvider`, in one tree too. In
   development a `willMount` still running after 10 s warns once.
5. A failed start goes to `onError(error, { type: 'start' })`, else to the console. With `errorFallback` the app shows
   it as the first root, the screen `ApexNavigation.StartFailure`, in place of the splash; its `retry` mounts again
   from `willMount`, with the same launch link (`launchLink()` keeps it), without the splash.
6. A later launch in the same runtime (Android recreated its activity) runs `restoreRoot()`: the last root set,
   `launchState.root`, as it was passed (functions in props included), is set again. Then, with `linking`, the new
   activity's link runs as in a running app (`linksRelaunched`).

## Links

`linking` of `createNavigation` (`src/api/linking/`) turns URLs into commands; the native side keeps no routes.

- **Matching.** `parseLink` takes the path and the query of a URL: after the longest of `prefixes` that ends at a part
  of the path; without `prefixes`, after the host of an `http(s)` URL or after `://` of another scheme. `compileRoutes`
  orders the patterns once (more required parts first, a static part before a param, fewer parts, then as written);
  `matchLink` takes the first that fits. No match: `unmatched(url)`, else a warning in development.
- **The launch link.** `getLaunchLink` of the native side gives it once (iOS `ANNDeepLinkQueue`, Android
  `LaunchLink.kt`), so a JavaScript reload doesn't run it again; iOS asks at `setupLinking`, before LogBox can flush the
  native queue (the URL; it is matched as the app starts). Its route's `start` can make the first root (`launchRoot`); a
  root the native side rejects is reported as the link's and the usual first root shows. Otherwise it runs after the
  first root (`linksStarted(link, handled, stateBefore)`), with the `url` events that came meanwhile (`early`), once the
  native side sent a newer state with a screen (`whenScreenAfter`, at most 3 s), so commands act from the new screen. A
  later `AppLaunched` (an Android activity in a live runtime) asks again (`linksRelaunched`).
- **Running a route** (`run`) as in a running app: a screen name is pushed with `{ ...query, ...params }` as its
  props, a function or `open` is called and awaited, a route with only `start` goes through `navigation.switchTo`. A
  throw or a rejection goes to `onError(error, { type: 'link', url })`, else to the console.
- **`when`.** A route's own, else the one of `linking`, read from `getAppContext()`. False: the link becomes the
  pending link (`pendingLinks.ts`, one, the latest). With `pending: 'auto'` every `setContext` (`subscribeAppContext`)
  checks it: after a turn of the event loop, so the commands that follow `setContext` have started, after
  `whenCommandsSettle` (at most 3 s) and, when commands ran, after a newer state (`whenScreenAfter`), the link runs if
  `when` lets it now. A `when` that throws keeps the link waiting. `openPendingLink` runs it whatever `when` says.

## One command

`navigation.push(name, config)` acts from the screen the user sees; `useNavigation().push` (`screenNavigation`) from
its own screen. Both reach `push` of `src/api/commands/commands.ts`:

1. `command('push', …)` wraps it, so a failure reads `[navigation] push: <reason>`; `placeFor` fails at once when the
   screen is gone or not in a stack.
2. `afterLoading([name])`: a lazy screen's module loads first, as its own options come from it.
3. `showOptions` builds the options in layers (below); `withSlots(() => engine.push(…))` sends the layout, so the slots
   of a failed push go.
4. `engine.push`: drops React's render measures, takes an id (`push13`), checks the ids, runs `toNativeLayout` (and
   `toNativeOptions` in it), notes the tabs and the places, then `whenNativeScreensReady` → `native.push` with the
   command id, the screen it is pushed from and the node.
5. On Android, in one tree, a push of a React screen is prepared first, so the screen comes in drawn without the
   native side waiting for it. Its slot is held (it renders nothing) in the task it is added in. `native.preparePush`,
   with the arguments of the push, lays the screen out in its stack, out of sight, and resolves its frame: its size
   and the padding of its safe area view. `showSlot` renders the slot at that size and the screen with its content at
   once, without `onReady`; once React committed it, `native.push` goes, and the native side starts the transition in
   the frame in which the content mounts. A null frame (`waitForRender: true`, a key that goes back, element
   transitions, …) shows the slot and pushes as above. A failure after the prepare calls `cancelPreparedPush`, and `withSlots` takes the slot
   back.
6. It resolves with the id of the screen shown. A screen with a key that went back to the one with the same key gives
   that one's id, and the new screen's props, place and slot go.

## Options in layers

The options of a show merge in this order, each over the one before (`showOptions`):

1. the kind of show: nothing for a push, the push's animation for `replace`, a transparent background for an overlay;
2. the options of the app's `presentation` for that kind;
3. the transition of the app's `presentation`, as `animations`;
4. the screen's own: its group's options, then those of its definition (`defineScreen`, `nativeScreen`);
5. the command's `transition`;
6. the command's `options`.

A modal then adds its close button to the screen and its style, sheet or popover to its stack; an overlay adds
`overlay.interceptTouchOutside`. The engine merges the screen's own options under these once more, which changes
nothing. The default options (`themedDefaultOptions`, `src/api/theme/theme.ts`) are layers of their own: the theme's
colors ← `safeArea` edges ← `options` ← `window` ← `direction`; `setTheme` and `setDirection` send them again.

The merge (`src/engine/mergeOptions.ts`): plain objects key by key; arrays (buttons), values and images replace what
was there; `undefined` keeps it.

## Ids and reserved names

- `nextId(prefix)` (`src/engine/ids.ts`): one counter for every prefix in a JavaScript run, so two ids never meet:
  nodes (`Component12`, `Stack`, `BottomTabs`, `SideMenuRoot`, `SplitView`, `ExternalComponent`, `Arrangement`), commands (`push13`,
  `setRoot`, `showColumn`), previews (`Preview14`). The ids the app gives (`.id()` of `Screen`, `Tab`, `Tabs`, a layout
  as JSON) are the only ones that can meet: `checkIds` fails on one id for two screens.
- The first screen of `showModal` is `ModalScreen<n>`, a counter of its own: its close button and `onDismiss` go by it.
- A screen with `key` goes with `data.key` = `<name>:<key>`: a push with the same key goes back to that screen in its
  stack. Slots and the hooks of native screens are keyed `<id>#<n>`, so a screen shown again under one id starts
  anew.
- Reserved: the screens `ApexNavigation.StartFailure` (`src/api/app/startFailure.tsx`), `ApexNavigation.Splash`
  (`src/api/app/splash.ts`) and
  `apexnative.navigation.buttonTab` (a tab without content, `src/api/layout/Tabs.ts`); the button ids `CloseButton` (a
  modal's close button, its testID too), `ApexNavigation.back` and `ApexNavigation.hardwareBackButton` (a held back
  press, `usePreventLeave`); the React root `apexnative.slots`; symbols named `apexnative.navigation.*`.

## Errors

A command that can't be made rejects with `[navigation] <command>: <reason>`, the same text on iOS and Android.
`command(name, run)` (`src/api/commands/errors.ts`) puts the command's name in front of what `run` throws, once.
`placeFor` tells what JS knows before the native side is asked: the screen is gone, it is not in a stack, it is the
first of its stack, it is not in a modal or an overlay. The engine fails before it sends anything on an unknown screen
name and on one id for two screens.

## Pairs, regions and the hinge

- **A pair** is a name of `screens` that `arrangementOf` knows: a `defineArrangement` entry, or a screen with
  `arrangement` (itself the primary). `toNativeLayout` makes it an `Arrangement` node, `{ id: Arrangement<n>, data:
{ name, style, axes, fraction, primaryEdge, compact }, children: [primary, secondary] }`, every setting filled in. The
  props of the show go to the primary and, through `secondaryProps`, to the secondary; the options of the show to the
  primary. Both children are built as screens of a pair: their own `arrangement` and `key` are not used, as their place
  is the pair's. `notePlaces` gives both the pair's place in its stack, with `arrangement: { id, placement }`.
- **Where the native side shows no pairs** (`getStartInfo().arrangements` false: iOS before 27.1, or built with an
  older SDK), the primary goes alone as a plain screen, the secondary is not built, and `noteAlone` marks the primary;
  `useArrangement` then answers `single` without asking the native side.
- **The stores.** `arrangementState.ts`, `screenRegions.ts` and `hinge.ts` each read the native side synchronously
  for the first render (`getArrangementState`, `getScreenRegions`, `getHinge`), keep the value by screen (the hinge:
  one for the app), and follow `ANN.ArrangementChanged`, `ANN.ScreenRegionsChanged` and `ANN.HingeChanged`. A
  screen's state and regions go when it unmounts (`screenLifecycle.ts`). The hinge store tells the native side to watch
  only while it has listeners (`setHingeUpdates`), and forgets its value when the last one goes.

## Previews

`usePreview(nativeID, { screen, props, actions })` works on iOS only. It arms a `pushPreview` from its screen: a push
with an id of its own (`Preview14`) and a `preview` option (`nativeID`, `commit`, the menu without its handlers, a
size). The native side makes the screen's node at once and gives the view with that `nativeID` a context menu that
previews it; a React screen gets its own root and renders when a press starts (the menu's configuration call, about
0.5 s before the preview), so a hundred previews hold no rendered screens until one is pressed. A screen of the one
tree (`appProvider`) and a native screen render at once. A tap on the preview pushes the screen, the push resolves, and
the hook arms the next one. A press of a menu item comes as `buttonPressed` of the preview's id, and the hook calls its
`onPress`. A preview that goes without being shown rejects its push, which the hook ignores. One that goes before its
first press never mounted a root, so the engine forgets what the push noted for it (`forgetUnmountedPreview` in
`src/engine/commands.ts`, after 1 s, unless a root of it mounted by then).

## The native side's contract

- Commands: the methods of `ANNTurboModule` (`src/adapters/NativeANNTurboModule.ts`), with the JSON above: those of
  `commands.ts`, each but `mergeOptions` and `setDefaultOptions` with a command id and a promise. A command that changed
  what the user sees sends `ANN.NavigationStateChanged` right before its promise resolves, so `getState()` after
  `await` shows its result and the next command acts from there. Besides them: `setBackListeners` (Android: whether
  JavaScript has `BackHandler` listeners; iOS does nothing), `preparePush` and `cancelPreparedPush` (Android, one
  tree: the screen of a push laid out before React renders it, or dropped; iOS resolves null), `getScreenInsets`
  (synchronous: a screen's insets for its first render, `useSafeAreaInsets`), `getScreenRegions` and
  `getArrangementState` (synchronous, the same for `useReservedRegions` and `useArrangement`), `setHingeUpdates` and
  `getHinge` (`useHinge`), `startHost` (the hidden root for
  `useProps`), `updateNativeScreenProps` (new props of an open native screen), `resolveNativeScreenCall` (the answer
  to `ANN.NativeScreenCall`), `getLaunchLink` (the URL that launched the app, or on Android the current activity,
  given once; JS asks only with `linking`) and `getStartInfo` (synchronous: `coldStart`, true for the whole life of
  the first JavaScript runtime of the process, and `splashLogo`, the size of the native splash's logo the CLI wrote,
  or null; `arrangements`, whether it shows pairs). The links that come while the app runs reach JS through React Native's
  `Linking`, not through `ANN.*` events. One exception, iOS only: a link the app dispatched (`dispatchDeepLinkURL`) that
  `Linking` doesn't take from the native side (React Native 0.88 and newer, with a scene delegate) comes as
  `ANN.DeepLinkDispatched`, and `src/api/linking/dispatchedLinks.ts` hands it to `Linking` as a `url` event.
- Events: `ANN.AppLaunched`, `ANN.ComponentWillAppear`, `ANN.ComponentDidAppear`, `ANN.ComponentDidDisappear`,
  `ANN.NavigationButtonPressed`, `ANN.BottomTabSelected`, `ANN.BottomTabPressed`, `ANN.BottomTabLongPressed`,
  `ANN.ModalDismissed`, `ANN.ModalAttemptedToDismiss`, `ANN.ScreenPopped`, `ANN.SearchBarUpdated`,
  `ANN.SearchBarSubmitted`, `ANN.SearchBarCancelPressed`, `ANN.SearchSuggestionPicked`, `ANN.PreviewCompleted`,
  `ANN.NavigationStateChanged`, `ANN.SlotDestroyed`, `ANN.ScreenInsetsChanged`, `ANN.TitleRenamed`,
  `ANN.BottomAccessoryPlacementChanged`, `ANN.SheetDetentChanged`, `ANN.SplitViewDisplayModeChanged`,
  `ANN.KeyCommandPressed`, `ANN.NativeScreenCall`, `ANN.NativeScreenReleased`, `ANN.ScreenRegionsChanged`,
  `ANN.ArrangementChanged`, `ANN.HingeChanged`, `ANN.SplitViewColumnsStacked`, `ANN.DeepLinkDispatched` (iOS). Their
  payloads are typed in `src/interfaces/ComponentEvents.ts` and `Events.ts`. Both platforms also send `ANN.CommandCompleted`
  (`CommandCompletedEvent`), which nothing in JS listens to.
- Native components: `ANNSlot` (`src/api/slots`) and `ANNSafeAreaView` (`src/api/safeArea`), with C++ of their own in
  `ios/cpp`, which Android builds too (their states hold a size and a padding; the first one comes from the props
  `initialSize` and `initialPadding` when they are given).

## Build

`yarn prepare` builds `src/` into `lib/` (ESM and TypeScript declarations); codegen reads the specs in `src/`
(`codegenConfig` in `package.json`).
