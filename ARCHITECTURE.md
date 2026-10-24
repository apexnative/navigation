# Native Architecture

The native core owns the navigation: stacks, tabs, modals, overlays, side menus, split views and pairs of screens. JS
builds a layout, sends it as JSON with a command, and hears the result as a promise and as events. The core is written
twice, in Objective-C++ on iOS (`ios/`) and in Kotlin on Android (`android/`), with the same layers, the same names for
the same rules and the same texts and event order. The options, the layout JSON, the commands and the events are the
contract of the types in `src/interfaces/`; both platforms implement it and
neither changes it.

The JavaScript half is in `src/ARCHITECTURE.md`. The maps of the files and the classes are `ios/CLAUDE.md` and
`android/CLAUDE.md` (each core's layers, flows and single places are in `ios/CORE.md` and `android/CORE.md`); this
file is the design they follow.

Paths: iOS relative to the repository root (`ios/Core/…`); Android under
`android/src/main/java/dev/apexnative/navigation/`, written once in full and then relative to it.

## 1. The picture

```
JS ── ANNTurboModule ─────────────────────────────────────────────────────────────────────────┐ sync getters
       │ async methods                                                                          │ read stores
       ▼ main queue (iOS) / module thread (Android)                                             ▼ (written on main)
   way in          iOS ANNTurboModule                     │ Android react/NavigationTurboModule
   layout check    ANNLayoutReader                        │ layout/LayoutReader (and options/OptionsReader)
   command runner  ANNCommandRunner, ANNCommandAnswer     │ react/CommandRunner, react/CommandAnswer
       ▼ main thread, one task per command, in call order
   registry        ANNNodeRegistry                        │ viewcontrollers/child/ChildControllersRegistry
   factory         ANNLayoutFactory                       │ viewcontrollers/navigator/LayoutFactory
       ▼
   nodes           ANNLayoutProtocol + ANNNodeHelper      │ viewcontrollers/viewcontroller/ViewController
                   (view controllers, UIKit containers)   │   ► ChildController (screens), ParentController (layouts)
   options         Options/ ANN<Group>Options             │ options/ one class per group, OptionsCache
       ▼
   coordinators    ANNRootCoordinator, ANNStackQueue,     │ navigator/RootCoordinator, stack/StackQueue,
                   ANNModalCoordinator,                   │ modal/ModalCoordinator,
                   ANNOverlayCoordinator, ANNRenderWait   │ overlay/OverlayCoordinator, component/RenderWait
       ▼
   presenters      ANNComponentPresenter,                 │ component/ComponentPresenter, stack/StackPresenter,
                   ANNTopBarPresenter,                    │ stack/topbar/TopBarPresenter,
                   ANNBottomTabsPresenter, …              │ bottomtabs/BottomTabsPresenter, …
                   ANNSystemUI                            │ system/SystemUiOwner
       ▼
   dispatcher      ANNAppearanceDispatcher                │ react/events/AppearanceDispatcher
   state, emitter  ANNNavigationState, ANNEventEmitter    │ react/events/NavigationState, EventEmitter
       ▼
   ANN.<Name> events ──► JS (iOS through ANNTurboEventEmitter, Android through React Native's device event emitter)
```

Each layer calls the next one down only. The answer of a command and the events go back up through the dispatcher, the
one place that sends them. Our modules (split view, pair, side menu, search, sheets, slots, safe area and others;
section 4) plug into the nodes, the presenters and the stores through the interface of section 4. The app-facing native
API wraps the launch: iOS `ApexNavigation`, `ANNAppDelegate`, `ANNSceneDelegate`; Android `NavigationApplication`,
`NavigationActivity`, `ReactGateway`.

## 2. The parts

### 2.1 Bridge

`ANNTurboModule` is the module JS talks to (codegen `ApexNavigationSpec` on iOS, `NativeANNTurboModuleSpec` on
Android). An async method keeps the call order and answers a promise; a sync getter only reads a store that the main
thread fills, so JS gets its answer during a screen's first render without waiting for the main thread. The module holds
no navigation logic: it turns a method into a call of the command runner.

- iOS: `ANNTurboModule` in `ios/TurboModules/`. Events leave through `ANNEventEmitter` and the module
  `ANNTurboEventEmitter`, which lists the supported event names (`ANNEventContractNames`) and keeps `ANN.AppLaunched`
  until JS listens.
- Android: `NavigationTurboModule` in `react/`, registered by `NavigationPackage`. Events leave through `EventEmitter`
  and React Native's device event emitter of the current React context.

### 2.2 Layout model and check

A layout node is immutable: `{ id, type, data, children }`. The whole tree is checked before any view exists: the types,
the ids, the names, the nesting, the registration of native screens, and ids that appear twice. The first error is the
text the command rejects with. The reader runs on the module thread; the checks that need the screen (an id that shows
already) run on main.

- iOS: `ANNLayoutReader` reads the JSON into `ANNLayoutNode`; `ANNLayoutValidator` checks what shows; the options of the
  tree are read afterwards on main by `ANNLayoutTreeOptions`; an image keeps only its source there and loads through
  `ANNOptionImages` when something shows it.
- Android: `LayoutReader` reads React Native's data straight into `LayoutNode` and calls `OptionsReader` in the same
  pass, so layout and options are read on the module thread; `LayoutChecks` holds the checks against what shows. Images
  load later, when the options apply.

### 2.3 Options model

A typed tree of groups, one class per group. Parsing is tolerant: a value of the wrong kind stays unset. One merge rule
(`mergeOptions`) and one rule of defaults. The options a node shows with are resolved in four layers, weakest first:
the defaults, its parents', its own, and its user layer; the result is cached per node. The user layer keeps what the
user chose in the native UI (a toggled bar button, the check of a single-selection menu, the search scope, the search
tokens and the suggestions the system removed; on iOS a renamed title) apart from the own options. It goes over the
rest by identity, a button and a menu item by their ids and a scope by its id, not as a tree merged over: a new list
keeps the choices of the same ids, and a button, item or scope that is gone takes its choice with it. An unrelated
`mergeOptions`, the same values sent again and `setDefaultOptions` keep a choice; a `mergeOptions` into the node that
gives the chosen field a value other than the node had before (its own options, or where they don't set it, what its
parents and the defaults gave) replaces it, as does one after which the button, item or scope is gone. A merge into a
parent doesn't. The suggestions the system removed are the exception: any `mergeOptions` that gives suggestions, the
same list too, shows them again. The layer goes with its node. The cache has
one invalidation point: a change of a node's own options, of its user layer, or of the defaults. A change of the
defaults repaints only what shows; a node that is hidden takes them when it shows. The resolved result is read-only;
code that wants to change it copies it first. Module options groups plug into the same tree.

- iOS: `ANNOptions` with a field table (`+optionFields`) that gives parsing, merging, copying and `hasValue` to every
  group; `ANNOptionsReader` reads, `ANNNodeHelper` resolves (`resolveOptionsWithDefault`), `ANNOptionsCache` is the
  generation, `ANNDefaultOptions` the defaults. Value kinds are in `Options/Params`. `ANNNodeHelper` also keeps the user
  layer (`setUserChoice:forKey:`; an `ANNUserChoice` is a write by identity and a rule for the app's merge).
- Android: `Options` is the root; each group writes `parse`, `mergeWith`, `mergeWithDefault`, `copy` and `hasValue`;
  value types are in `options/params/`, tolerant readers in `options/parsers/`. `ViewController.resolveCurrentOptions`
  caches against `OptionsCache`; `DefaultOptions` holds the defaults. A test walks the whole tree by reflection, so a
  field that is not read, copied, merged or defaulted fails it. The user layer is `UserChoices` per node, written
  through `ViewController.changeChoices`, applied in `resolveCurrentOptions(defaultOptions)` and yielding in
  `mergeOwnOptions`.

### 2.4 Node tree and registry

Every layout node becomes a runtime node: a screen (content: a React root, a slot of the app's one React tree, or a
native screen), a stack, tabs, a side menu, a split view, a pair. Nodes find their parent and their children through the
layout, not through the platform's view tree. The registry holds every node a command may name, is filled when a node is
made, emptied when it goes, and is empty after a JS reload. A modal and an overlay are records of their coordinators that
hold a node.

- iOS: every node is a `UIViewController` that adopts `ANNLayoutProtocol` and keeps an `ANNNodeHelper`. Containers
  inherit UIKit's (`UINavigationController`, `UITabBarController`, `UISplitViewController`,
  `UIArrangementViewController`), so they cannot share a base class; the protocol and the helper are the shared part.
  `ANNNodeRegistry` is one weak map for the process.
- Android: views only, no Fragments. `ViewController` is the base of every node; `ChildController` is a screen and
  `ParentController` the base of a layout. A node makes its view in `createView`, not in its constructor.
  `ChildControllersRegistry` is one per `Navigator`, and `Navigator` is one per activity.

### 2.5 Lifecycle and events

Each screen has an appearance record: `ComponentWillAppear`, `ComponentDidAppear`, `ComponentDidDisappear`, once per
appearance and balanced. Containers report the show and hide of their children. An event asked before the content
mounted is held until it mounts, at most 0.5 s, so a hold never delays a transition. One dispatcher sends the events
synchronously, in the order of section 5. The navigation state is computed from the visible chain (the screen the user
sees and the nearest tabs it is in) and is sent before each answer that changes what shows. Destroying a node
stops its React surfaces on main, clears its stores and sends `NativeScreenReleased` and `SlotDestroyed`; after a JS
reload the events of the old JS are silent.

- iOS: `ANNScreenAppearance` per content, `ANNAppearanceDispatcher`, `ANNNavigationState` (coalesced updates, holds, the
  state now), `ANNEventEmitter` (drops events once its generation moved).
- Android: `ScreenAppearance`, `AppearanceDispatcher`, `NavigationState` (coalesced updates, holds, the state now),
  `EventEmitter`. The dispatcher reaches the state through `AppearanceDispatcher.StateUpdates`.
- The state before an answer goes once per answer, from the answer (`-[ANNCommandAnswer reportStateNow]`,
  `CommandAnswer.reportStateNow`): at the end of its transition, or before its promise.

### 2.6 Coordinators

A coordinator owns the rules that span nodes and never touches React Native's views.

- **Root** (`setRoot`): at most one pending, the last wins; the new root is made and rendered out of sight under the
  current one, waits for its render, then replaces it by a fade or its own animation. The layout's modals and overlays
  show after it, one at a time, with the navigation state held until the answer. The root also owns the splash and the reset after a reload.
- **Stack**: one queue per stack. Every change of the stack's children, from a command, from back or from the user's
  swipe, is an operation in it. The queue keeps `entries` (what JS sees) and what the container shows, decides per
  command whether it interrupts the running one or waits, and answers once.
- **Modal**: one record per modal with its states and the commands waiting for it.
- **Overlay**: one record per overlay; no animation and no part of the state.
- **Readiness**: the render wait with the limits for `waitForRender`, the bar components and unknown content, fed by
  the signals of `ANNSafeAreaView` and of the slot.

iOS: `ANNRootCoordinator`, `ANNStackQueue`, `ANNModalCoordinator` with `ANNModal` and `ANNPresentationGate`,
`ANNOverlayCoordinator`, `ANNRenderWait`, `ANNScreenReadiness`, `ANNPendingLayers`, `ANNParking`.
Android: `RootCoordinator`, `StackQueue`, `ModalCoordinator` with `ModalRecord`, `OverlayCoordinator` with
`OverlayRecord`, `RenderWait`, `ScreenReadiness`, `ContentReadiness`, `WaitingLayer`. The Android coordinators talk to
views through small interfaces (`StackDisplay`, `ModalCoordinator.Views`, `RootCoordinator.Layer`) and to the clock
through `AppearanceDispatcher.Scheduler`, so the unit tests run them with fakes.

### 2.7 Presenters

A presenter applies the resolved options to the platform: the screen (background, insets, keyboard), the top bar, the
tabs, the side menu, the modal styles and sheets, the transitions, the system UI. It reads options, writes platform
state and sends no appearance event. After any change it takes the resolved options of the top screen and applies only
what changed. It applies when the screen shows, when options are merged, and again on new defaults and on a light or
dark switch. The system UI has one owner per window or activity.

- iOS: `ANNComponentPresenter`, `ANNTopBarPresenter`, `ANNStackPresenter`, `ANNBottomTabsPresenter`,
  `ANNSideMenuPresenter`, `ANNSplitViewPresenter`, `ANNSheetPresenter`, `ANNTransitionPlan` with
  `ANNTransitionAnimator`, and `ANNSystemUI` (marks groups, asks UIKit once per turn).
- Android: `ComponentPresenter`, `StackPresenter`, `TopBarPresenter`, `BottomTabsPresenter`, `SideMenuPresenter`,
  `SplitViewPresenter`, `ModalSheet`, `StackTransitions`, and `SystemUiOwner` (changes only mark it; before the next
  frame it computes the state with `SystemUiRules` and `SystemUiWindow` writes the difference).

### 2.8 App-facing API and launch

- iOS: `ApexNavigation` (`bootstrapWithHost:`, `registerNativeScreen:…`, `findViewController:`), `ANNAppDelegate`,
  `ANNSceneDelegate`, the splash (`ANNSplashViewController`), reload by `ANNReloadCoordinator`. Apps import the headers
  of `ios/Public/ApexNavigation/`.
- Android: `NavigationApplication` (React host through `ReactGateway`, made on first use after the app's
  `loadReactNative(this)` in `onCreate`; native screens by name), `NavigationActivity`
  (layers, splash hold, back, developer reload), `NavigationPackage`, edge-to-edge (`EdgeToEdgeDecision`), the splash
  (`SplashHold`, `SplashView`, `SplashTheme`). `ANN.AppLaunched` goes once per pair of an activity and a React context.

## 3. The single places

Each rule has one owner. A feature that needs the rule calls the owner; it does not copy the rule.

| Seam                        | iOS                                                                                                           | Android                                                                                                      |
| --------------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Way in                      | `ANNTurboModule`; every async method but `preparePush` through `ANNCommandRunner`                             | `NavigationTurboModule`; every async method through `CommandRunner`                                          |
| Layout check                | `ANNLayoutReader` on main, in the command's task before its body                                              | `LayoutReader` on the module thread; `LayoutChecks` on main                                                  |
| Answer                      | `ANNCommandAnswer`: state, promise, `CommandCompleted`; once                                                  | `CommandAnswer`: the same, through `AnswerEvents`                                                            |
| Error texts                 | `ANNErrorTexts`, made from `src/interfaces/contract/errors.json`                                              | `ErrorTexts`, made from the same file                                                                        |
| Event names and keys        | `ANNEventContract`, made from the event types of `src/interfaces/`                                            | `EventContract`, made from the same types                                                                    |
| Registry                    | `ANNNodeRegistry`, one for the process, weak                                                                  | `ChildControllersRegistry`, one per `Navigator`; `Navigators` holds the activities weakly                    |
| Factory                     | `ANNLayoutFactory`; `ANNSideMenuPresenter` is the one other file naming a module                              | `LayoutFactory`                                                                                              |
| Options cache               | `ANNOptionsCache`, moved by the setter of `ANNNodeHelper.options`, by `setUserChoice:forKey:` and by defaults | `OptionsCache`, moved by the `ViewController.options` setter, by `changeChoices` and by `DefaultOptions.set` |
| Options a layout shows with | the node's `resolveOptions` (own, then the current child down)                                                | `resolveShownOptions`                                                                                        |
| Appearance dispatcher       | `ANNAppearanceDispatcher`                                                                                     | `AppearanceDispatcher`                                                                                       |
| Navigation state            | `ANNNavigationState`: `holdUpdates`, `releaseUpdates`, `updateNow`                                            | `NavigationState` through `AppearanceDispatcher.StateUpdates`: `hold`, `release`, `updateNow`                |
| Answer id of a stack entry  | `ANNStackAnswerIdOf` in `ANNStackQueue.h`                                                                     | `StackController.answerIdOf`, asked by the queue through `StackQueue.Host`                                   |
| Screens of an entry         | `ANNStackScreensOfEntry`                                                                                      | `StackController.screenIdOf`, `optionsScreen`                                                                |
| Screen readiness            | `ANNScreenReadiness`, by screen id                                                                            | `ScreenReadiness` and `ContentReadiness`                                                                     |
| Render wait                 | `ANNRenderWait`                                                                                               | `RenderWait`                                                                                                 |
| Waited screens              | `waitedComponentViewControllers`                                                                              | `ParentController.waitedChildren`                                                                            |
| Waiting place               | `ANNParking`: the window, a stack's view, a preview's host view                                               | the root under the current one (`RootLayerViews`), the stack's own view (`StackViews`)                       |
| Waits to show               | `ANNPendingLayers`                                                                                            | `WaitingLayer`, kept by `Navigator`                                                                          |
| What a node shows changed   | `ANNShownScreens`: pending layer, modal hold, system UI                                                       | `Navigator.shownScreensChanged`: waiting layer, parent stack's queue, modal sheet                            |
| Modal records               | `ANNModalCoordinator`, `ANNModal`                                                                             | `ModalCoordinator`, `ModalRecord`                                                                            |
| Presentation gate           | `ANNPresentationGate`: every UIKit present and dismiss in the main window                                     | none: transitions overlap                                                                                    |
| Overlays                    | `ANNOverlayCoordinator`, a window each                                                                        | `OverlayCoordinator`, a layer of the activity                                                                |
| System UI owner             | `ANNSystemUI`, the rules in `ANNSystemUIRules`                                                                | `SystemUiOwner` per `Navigator`; only `SystemUiWindow` writes to the window                                  |
| Reload                      | `ANNReloadCoordinator`: the generation of the JavaScript                                                      | `ReloadCoordinator`: the generation; `ReactGateway` tells it                                                 |
| Room for the bars           | `ANNScreenSafeArea`, `ANNScreenInsets`                                                                        | `ScreenFrame`, `ScreenSafeArea`; `ScreenFrame.topRoomOf` for a screen and a pair                             |
| A container's own move      | the container's UIKit callbacks, told to `ANNAppearanceDispatcher`                                            | `ShownChildren`, through the dispatcher's order                                                              |
| Back                        | `ANNStackController`, `InteractivePopGestureDelegate`, `uikitShows:` of the queue                             | `NavigationActivity` → `Navigator.handleBack` → `StackQueue`; `BackCallbackState`                            |

Rules that belong to several rows:

- A stack child changes only through the stack's queue. UIKit's own pops (swipe back) are followed, not fought.
- The navigation state is held in pairs: every hold has one release. A `setRoot` with modals or overlays in its layout
  holds it until its answer, so JS hears one state.
- The state's `tab` is the selected tab of the nearest tabs the visible screen is in, on both platforms.
- A wait is a value of the render wait (limit, bar phase, unknown-content rule), never a timer of its own.
- A layer that waits to show leaves on every exit: show, supersede, destroy, reload.

## 4. The module interface as built

Our modules (the split view, the pair, the side menu, search, sheets, menus, the slot and safe area views, native
screens, regions, links, the FAB and others) are code that stays; the core is written so that they plug in. The core
names a module class in one place only: the factory.

**Makers.** The factory has one maker per layout type. It is the one core code that names the module containers: iOS
`ANNLayoutFactory` (`ANNSplitViewController`, `ANNSideMenuViewController`, `ANNArrangementViewController`, plus
`ANNSideMenuPresenter` for the side menu's presenter); Android `LayoutFactory` (`SplitViewController`,
`ArrangementController`). It asks `canMake` before a command makes anything.

**Container contract.** A container gives the core its children and answers its questions:

- iOS: the node protocol `ANNLayoutProtocol`. Required: the current child (`getCurrentChild`), all children
  (`layoutChildren`), the screens shown now, `render`, `readyForPresentation`, `waitForRender`, `resolveOptions`,
  `mergeOptions:`, `mergeChildOptions:child:`, `destroy`, `systemUIChild`. A container does the common part with its
  `ANNNodeHelper` (data, own and resolved options, ready handlers by key, registry entry) and never through `super`.
- Android: a container extends `ParentController` and overrides `getChildControllers`, `getCurrentChild`,
  `visibleChildren()` (what appears with the layout; several for a side menu or two panes), `waitedChildren()` (what a
  wait for rendering waits for), and `resolveChildOptions`, `applyChildOptions`, `mergeChildOptions`. The room hooks
  (`getTopInset`, `keepsStatusBarRoomOf`, …) carry the bars' room down to the screens.
- Both: when what a container shows changes it tells the one seam (`ANNShownScreens` on iOS, `Navigator.shownScreensChanged`
  on Android). When children move in or out of the window without a command, the container reports it so that the
  appearances go in the dispatcher's order (Android `ShownChildren`; on iOS UIKit's callbacks reach the dispatcher).

**Capabilities.** A capability is an optional answer of a node; a node without it answers "no". The core asks for the
capability, not for the class.

| Capability                              | iOS                                                                          | Android                                                           |
| --------------------------------------- | ---------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Returns a screen from a column          | `goBackFromColumnOf:completion:`                                             | `ReturnsScreenFromColumn.returnsScreenFromColumn`                 |
| Screens that leave with the node        | `screensLeavingWithNode`                                                     | `ScreensLeavingWithNode.screensLeavingWithNode`                   |
| Child offset from rest                  | `childOffsetFromRest:`, a point                                              | `ChildOffsetFromRest.childOffsetFromRest`, pixels along the width |
| The screen whose options are the node's | `optionsScreen`                                                              | `OptionsScreen.optionsScreen`                                     |
| Keeps the status bar's room             | `keepsStatusBarRoom`, a method                                               | `KeepsStatusBarRoom`, a marker interface                          |
| Columns                                 | `viewControllerOfColumn:`, `isCollapsedIntoCompact`, `showScreen:inColumn:…` | `ShowsColumns`: `showScreen`, `setColumnShown`                    |
| Waited screens, readiness               | `waitedComponentViewControllers`, `waitedReadinessOfScreen:`                 | `waitedChildren`, `waitedUnseenChildren`                          |
| A screen appeared while driven          | `drivenDescendantDidAppear:`, `childDidDisappear:`                           | none; the tabs end their switch themselves                        |
| A screen appeared by UIKit alone        | `descendantDidAppear:` (a collapsed split view follows its column return)    | none; the split view calls `shownScreensChanged` itself           |

The iOS core asks with `respondsToSelector:`; the Android core finds the nearest node that has the capability with
`nodeWith`. The shapes differ because the languages do: an Objective-C optional method against a Kotlin interface to
implement. The behavior is the same. On iOS `childOffsetFromRest:` is declared and no node answers it; the pair
answers `keepsStatusBarRoom` and the core does not ask it. `ShowsColumns` has no iOS twin: iOS asks the split view for
the controller of a column (`viewControllerOfColumn:`), and `ANNSplitViewCommands` moves the screen.

**Content.** What a screen shows is behind one interface, so a screen does not care who made the content.

- iOS: `ANNComponentProtocol` (`React/ANNReactView.h`): id, type, appearance record, ready flag, mount handler by key,
  `stopSurface`, touches. Made by `ANNComponentViewCreator`: `ANNReactView` is a React root of its own,
  `ANNSlotScreenView` is a screen of the app's one React tree (a name that starts with `slot:`).
- Android: `IReactView`: `ReactView` for a React screen, `SlotScreenView` for a name that starts with `slot:`
  (`ScreenContentFactory`), and the view of a pair, which stands for its primary.
- Native screens: iOS `ANNExternalViewController` (a screen whose content a native creator made), registered with
  `registerNativeScreen:`; Android `ExternalComponentViewController`, registered on `NavigationApplication`. Both give
  the creator a context with the props, the functions to call in JS and a way to navigate (`ANNNativeScreenContext`,
  `NativeScreenContext`).

**Presenter hooks.** A module presenter applies options when the screen shows, when options are merged, and when the
defaults or the theme change.

- iOS: `ANNNodePresenter`: `applyOptionsOnInit:`, `applyOptions:`, `mergeOptions:resolvedOptions:`,
  `applyDefaultOptionsChange:`, `descendantWillAppear:`, `mergeChildOptions:`, `optionsForParent:`, and the answers to
  UIKit (status bar, orientations, home indicator, deferred edges).
- Android: `Presenter`: `applyOptions`, `mergeOptions`, `onViewBroughtToFront`, `onConfigurationChanged`. The split
  view gets its options from its module; `SplitViewPresenter` hands it the new defaults through `setSplitOptions`.

**Option groups.** iOS: a subclass of `ANNOptions` overrides `initWithDict:`, `mergeOptions:` and `hasValue`. Android: a
class with `parse`, `mergeWith`, `mergeWithDefault`, `copy` and `hasValue`, added to `Options`.

**Stores.** Insets, regions, the pair's state, the hinge and the start info (`ANNScreenInsets`, `ANNScreenRegions`,
`ANNArrangementState`, `ANNHinge`; `ScreenInsets`, `ScreenRegions`, `ArrangementStates`, `Hinge`, `StartInfo`) are
written by the core on the main thread and read by the sync getters from any thread, behind `@synchronized` on iOS and
`ConcurrentHashMap` or `@Volatile` on Android. They are cleared with their screens and on reload.

**Core code in a module's folder.** Two classes live in a module folder and are core: iOS `ANNSplitViewPresenter`
and `ANNSplitViewCommands` in `ios/Layouts/SplitView/`, Android `SplitViewPresenter` in `viewcontrollers/splitview/`
(and `SlotScreenView` in `slots/`). The core owns them; only the factory (and `ANNSideMenuPresenter`) names the module
class they work with.

## 5. Threads, event order, errors

**Threads.**

- The sync getters run on the JavaScript thread on both platforms. On iOS the module's methods come on the main queue,
  where `ANNCommandRunner` checks the layout (`ANNLayoutReader`) and runs the body in one task; the options are read
  on main. On Android the module thread runs the layout check and reads the options of every command
  (`OptionsReader`, also for `setDefaultOptions`, `mergeOptions` and the options of `dismissModal`), so the check
  reads only data that is safe from any thread.
- The main thread runs everything else: the command body, nodes, views, events, the writes to stores. One task per
  command, in call order. A command does nothing when its JavaScript went.
- JS reads the sync getters during the first render of a screen, so they never wait for main. `ReloadCoordinator.generation`
  (Android) and `ANNReloadCoordinator.generation` (iOS) are readable from any thread.

**The order of events at the end of a transition**, the one order for every command that shows or hides screens:

1. `ComponentWillAppear` of what comes, at the start (`beginTransition`), held until its content mounted, at most 0.5 s.
2. At the end (`endTransition`): `ComponentDidDisappear` of what left, then `ComponentDidAppear` of what came.
3. `NavigationStateChanged`, if the state changed: the answer's one state step.
4. The transition's own events (`ScreenPopped`, `ModalDismissed`; none for a push).
5. The promise resolves with the id of the screen shown.
6. `CommandCompleted`, for the commands that show or dismiss.

The answer sends the state once: at step 3, or, for a command answered without a transition's end (a `setRoot` with
modals or overlays in its layout), right before step 5, so JS `await`s a command and finds its result in `getState()`.
The answer is sent once; nothing is sent for a JavaScript that went, the state neither. A command without a promise
(`mergeOptions`) sends no state and no `CommandCompleted`.

**A tab press**, the same for every source (the system bar, the iPad tab strip and sidebar, the custom row, Android's
Material bar), through one path (`-[ANNBottomTabsController pressTabAtIndex:source:]`,
`BottomTabsController.onTabPressed`):

1. A switch of the tabs still running ends, with its `BottomTabSelected`.
2. `BottomTabPressed { tabIndex, bottomTabsId, screenId?, wasSelected }`.
3. Another tab: `ComponentWillAppear` of its screen; at the end of the switch `ComponentDidDisappear` of the screen
   that was left and `ComponentDidAppear` of the new one; then `BottomTabSelected`, the left tab's `popToRootOnBlur`
   (its `ScreenPopped`) and the navigation state, once. The selected tab again: the pop of the user's back to the
   first screen, or a scroll to the top; with `popToRootOnPress: false` nothing. `selectTabOnPress: false`: nothing.
4. A long press sends `BottomTabLongPressed` only.

A choice from code (`bottomTabs.currentTabIndex`, `currentTabId`) and Android's back between tabs send the
appearances and the state, without the press events. The state goes at once, at the start of the switch, so
`selectTab`, which waits for it, resolves before the next command and that command acts in the new tab.

**The table.** Both platforms answer from one table: the error texts of `src/interfaces/contract/errors.json`, the
commands with a promise in `src/interfaces/contract/commands.json` (checked against the async methods of the spec:
whether the state and `CommandCompleted` go with each answer, and what each resolves with, written down there), and
the event names and payload keys of the types in `src/interfaces/` (`Events.ts`, `ComponentEvents.ts`).
`scripts/contract.mjs` makes `ANNErrorTexts`, `ANNCommandContract` and `ANNEventContract` (iOS) and `ErrorTexts`,
`CommandContract` and `EventContract` (Android) from them; the files are committed, and `scripts/check.sh` fails when
one differs from its source. A text, a key or a command's list is never written by hand. Which text a case gives and
what a command resolves with are behavior, kept by each platform's code (the resolve values are not checked by the
table): the same on both platforms, apart from what section 6 lists.

**Errors.** A command that fails on the native side rejects its promise with a text from the error table; JS adds
`[navigation] <command>:`. The check rejects before a node exists; a failure in the body rejects with `could not
<what>: <reason>`; a failure in a step that runs later (a ready handler, a timer, the end of a transition) is caught by
the owner's guard and rejects the command too. On iOS an answer that goes away unanswered while its JavaScript runs
rejects `could not run '<command>': the native core let it go without an answer`, so a path that forgets to answer
never leaves the promise waiting. A command without a promise writes one line to the log, `[ApexNavigation]
<command>: <text>` (a warning on iOS, `Log.e` on Android). An exception never ends the app.

## 6. Where the platforms differ on purpose

The design is the same; these are the differences that come from the platform or from what UIKit and Android do by
themselves.

- **Containers.** iOS containers are UIKit's (`UINavigationController`, `UITabBarController`, `UISplitViewController`,
  `UIArrangementViewController`), so they implement `ANNLayoutProtocol` rather than inherit from a core class. Android
  has its own view-based containers under `ParentController`, no Fragments.
- **Presenting.** iOS presents modals through UIKit, which drops a transition asked for while another runs, so every
  present and dismiss is a step in `ANNPresentationGate`. Android modals are layers of the activity and overlap.
- **Waiting place.** iOS parks a waiting view in the window, a stack's view or a preview's host (`ANNParking`). Android
  puts a waiting root under the current root (`RootLayerViews.place`) and a waiting push under the stack's top
  (`StackViews.placeWaiting`), with a transparent container that gives its touches away while it waits.
- **Windows.** iOS overlays live in windows of their own (`ANNOverlayWindow`); `ANNSystemUI` serves the
  main and the overlay windows. Android has one activity, one owner of the system UI (`SystemUiOwner`) and one
  navigator. After the activity is recreated the new one gets its own `Navigator` and its own `ANN.AppLaunched`.
- **System UI.** iOS answers UIKit's questions (status bar, orientations, home indicator) from the resolved options and
  asks UIKit again once per turn. Android computes a state and writes it to the window itself, edge to edge.
- **Back.** iOS: the back button and the swipe-back gesture. Android: the system back goes through JS's `BackHandler`
  first, then into the navigation (`Navigator.handleBack`); `BackCallbackState` keeps the system callback on only while
  back has something to do. JS tells Android whether it listens (`setBackListeners`).
- **Events.** iOS sends through the module `ANNTurboEventEmitter`; Android through the device event emitter of the
  React context, and can send without an activity.
- **Options reading.** iOS reads a tree's options on main; Android reads layout and options on the module thread. So
  an unreadable option rejects on Android before the checks of the body (the screen isn't shown, isn't in a stack, the
  id shows already); on iOS after them. Both load an option's image through one cache when it shows (iOS
  `ANNOptionImages`, Android `OptionImages`): at once where the first frame needs it (the top bar before a push, a
  tab's first icon, a new FAB), off the main thread for a file of the app where the view takes it when it is ready (a
  tab's changed icon; on Android also the FAB's).
- **Platform-only checks and logs.** Only Android rejects more than 5 tabs (`tooManyTabs`: its tab bar shows at most
  5); only iOS rejects a pair before iOS 27.1 (`pairUnsupported`). Only Android logs a failed `preparePush`, which iOS
  answers with `null`. Only iOS has `notAnswered`. A command whose promise resolves with nothing gives JS `undefined`
  on iOS and `null` on Android (`setColumnShown`).
- **Prepared push.** `preparePush` lays a screen out before React renders it, so a push can start in the frame the
  content mounts. Android does it (`PreparedPushes`); iOS answers null and the push goes the usual way.
- **Pair and hinge.** The pair is `UIArrangementViewController` on iOS 27.1 and later (built with that SDK; before it
  the app has no pairs, `canShowArrangements`). Android has a pair of its own (`ArrangementController`,
  `ArrangementPlanner`) on every version, and the hinge comes from `WindowFolds` and the sensors.
- **Platform-only features.** iOS: previews (`ANNPreviewInteraction`), key commands (`ANNKeyCommands`), toolbar
  (`ANNToolbarOptions`), the tabs' accessory (`ANNBottomTabsAccessoryView`), scenes (`ANNSceneDelegate`). Android: the
  FAB (`FabPresenter`), the custom tab row (`CustomTabRowPlacer`), the Material tab bar (`BottomTabs`), the splash
  hold (`SplashHold`), edge-to-edge. On Android the preview and key command options are parsed and merged and not
  applied.
- **Bars.** iOS uses UIKit's navigation bar, large title and tab bar. Android draws them (`TopBar`, `LargeTitleView`,
  `BottomTabs`) and follows the scroll of the content (`ScrollEventListener`).
- **Unanswered commands.** iOS rejects a command whose answer goes away unanswered (`ANNCommandAnswer` `dealloc`).
  Android can't see an answer go (the garbage collector decides when), so there every path answers by itself.
- **Tests.** Android has JUnit 4 unit tests on the JVM (`android/src/test/java/dev/apexnative/navigation/`), one package
  per main package; coordinators and rules run with fakes. iOS has no unit-test target; the Showcase scenarios check it.

Native code on both platforms never touches React Native's views (their subviews, their layout, their frames): Fabric
owns and recycles them. Slots are moved through `SlotContainers`, which keeps the containers and the slot views by slot
id: a change of the container of an id goes straight to the slot views under it (a map of weak views, kept on mount,
recycle and unmount), never to every slot. The C++ of `ANNSlot` and `ANNSafeAreaView` in `ios/cpp/` is built by both
platforms (`android/src/main/jni/CMakeLists.txt`).
