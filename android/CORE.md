# Android core: structure

The layers of the Android core, how a command flows through them, the classes that own each rule and how modules plug
in; read it when changing the core's structure, adding a module or tracing a flow. Paths are under
`android/src/main/java/dev/apexnative/navigation/`, as in `android/CLAUDE.md`; main thread only, unless a line says
otherwise.

## Layers

```
JS ─ ANNTurboModule (codegen NativeANNTurboModuleSpec)
 │      sync getters answer on the JS thread from stores the main thread fills
 │      (ScreenEnvironment, Hinge, StartInfo)
 ▼ module thread
react/NavigationTurboModule      one override per method
 │  layout/LayoutReader ► options/OptionsReader     the whole layout read and checked, its options read
 ▼ main thread, one task per command, in call order
react/CommandRunner ── react/CommandAnswer ── react/ErrorTexts
 ▼
viewcontrollers/navigator/Navigator       one per activity: layers, registry, commands of the root,
 │                                        modals, overlays; stackCommands for the stacks
 ├ child/ChildControllersRegistry         id → node
 └ navigator/LayoutFactory                LayoutNode → node (the only code that names module containers)
 ▼
nodes: ViewController → ChildController (screens) / ParentController (stack, tabs, side menu, split view, pair)
 │ options: options/Options (typed tree) + OptionsCache + DefaultOptions, resolved and cached per node
 ▼
coordinators: RootCoordinator · StackQueue (one per stack) · ModalCoordinator · OverlayCoordinator ·
              RenderWait + ScreenReadiness (waits for screens to render) · WaitingLayer
 ▼
presenters: ComponentPresenter · StackPresenter + TopBarPresenter · BottomTabsPresenter · SideMenuPresenter ·
            SplitViewPresenter · system/SystemUiOwner (status bar, navigation bar, orientation, keyboard)
 ▼
react/events/AppearanceDispatcher ─► NavigationState ─► EventEmitter ─► ANN.* events ─► JS
```

- **Way in.** `NavigationTurboModule` only turns a method into a `CommandRunner` call; it holds no navigation logic. It
  may call the runner, the readers, `Navigator`, `StackCommands` and the stores.
- **Layout check.** `LayoutReader` reads React Native's data straight into immutable `LayoutNode`s and checks the whole
  tree (types, ids, names, nesting, native-screen registration) before anything is queued. It calls `OptionsReader`.
  `LayoutChecks` is its main-thread half: ids that already show.
- **Command runner.** `CommandRunner` takes the number of the JavaScript (`ReloadCoordinator.generation`), runs the
  check, posts one main-thread task and makes the `CommandAnswer`. It rejects, never crashes. It calls `Navigator` and
  what the module method hands it.
- **Registry and factory.** `ChildControllersRegistry` and `LayoutFactory` make and find nodes. The factory calls the
  node constructors and nothing else.
- **Nodes.** A node owns its view (made in `createView`, not in the constructor), its own options and its place in the
  tree. It calls its parent through `ParentController` hooks and its presenter.
- **Options.** The typed tree is immutable by convention: a resolved result is cached and read-only; code that changes
  options copies them first.
- **Coordinators.** Own the rules that span nodes: the order of a stack's operations, a modal's states, a root's
  `setRoot`, the waits. They talk to views through small interfaces (`StackDisplay`, `ModalCoordinator.Views`,
  `RootCoordinator.Layer`) and to the clock through `AppearanceDispatcher.Scheduler`, so unit tests run them with
  fakes.
- **Presenters.** Apply resolved options to views and to the system UI. They call views and the system UI owner, never
  a coordinator.
- **Dispatcher, state, emitter.** Everything JavaScript hears leaves here. Layers above call `AppearanceDispatcher`;
  only it calls `EventEmitter` for appearance events.

## How a command flows: `push` of Profile onto Home's stack

1. **JS thread.** `NavigationTurboModule.push` calls `CommandRunner.run` with the command's name, its id (`push13`), a
   `what` text for failures, the promise and a `check`.
2. **Module thread.** `check` runs `LayoutReader.node`: the layout and the options of every node are read and checked.
   A `LayoutException` rejects the promise with its text; nothing is queued.
3. **Main thread.** `run` posts one task (`UiThread.post`), in call order. It drops the command if its JavaScript went
   (`ReloadCoordinator.isCurrent`), makes the `CommandAnswer`, rejects with `ErrorTexts.nowhereToShow` without an
   activity, else runs the body inside `CommandScope`. An exception rejects with `ErrorTexts.couldNot`.
4. **`Navigator.stackCommands.push`** (`StackCommands`). The checks come before any node: the screen is shown, it is in
   a stack (`ErrorTexts.notInStack`), the new id doesn't show already (`LayoutChecks.firstShownId`), `LayoutFactory`
   can make the tree. A key of an entry goes back to it (`backToKey`).
5. **Make and join.** `LayoutFactory.create` makes Profile (a `ComponentViewController`, registered in the
   `ChildControllersRegistry`); `StackController.join` adds it; `StackQueue.push` accepts it as an entry.
6. **`StackQueue`** is the only way to change a stack's children. `drain` starts the operation when nothing blocks it.
   `startPush` decides the wait: a prepared push; `waitForRender: true` (as long as it takes); unset, for a screen with
   React content, Profile's first mount, 0.5 s at most (`PUSH_WAIT_MILLIS`); none for `false`, a native screen or a
   stack off screen. A wait is a `RenderWait` on Profile. Its mount reaches the push through the queue's `afterCalls`
   (`PendingCalls`: once through the JavaScript thread, the module's thread and the main thread, the way of a
   `mergeOptions`), so the `mergeOptions` of the commit that mounted it (`useScreenOptions`) has applied when the
   transition starts, content mounted before the push too; the prepared push starts the same way after its slot
   mounted. Unset, the targets of elements take what is left of the 0.5 s.
7. **Begin.** `beginPush` raises Profile's view (`StackViews`), moves the bar and the tab bar
   (`StackQueue.Host.barTransition`, `tabBarTransition`), calls `StackController` through `currentChildChanged`, then
   `AppearanceDispatcher.beginTransition`: **`ComponentWillAppear`** of Profile. The transition runs
   (`StackTransitions`, `SystemTransition`, or the screens' own animation, `AnimationRules.stackIsOwn`).
8. **End.** `finishPush` calls `AppearanceDispatcher.endTransition`: **`ComponentDidDisappear`** of Home, then
   **`ComponentDidAppear`** of Profile, then the answer's one state step (`CommandAnswer.reportStateNow`:
   **`NavigationStateChanged`**, only if it changed), then the transition's own events (none for a push), then
   `CommandAnswer.resolve` with Profile's id.
9. **Answer.** `resolve` sends the state only when no transition sent it (`reportStateNow` runs once per answer: a
   `setRoot` with layout modals sends it here), resolves the promise, then sends **`CommandCompleted`**
   (`CommandContract.COMPLETED`). After the task, `BackCallbackState.update` recomputes what back does.

The events go from the main thread, synchronously, through `EventEmitter.current()`; the promise is answered once.

- **`setRoot`** (`Navigator.setRoot`, `RootCoordinator`). Not a stack operation. The new root's view goes under the
  current root in the root layer, transparent (`RootLayerViews.place`), so its React roots start while the old root keeps
  the touches. It waits (`RenderWait`, by `animations.setRoot.enter.waitForRender`), then fades through or plays its own
  animation, the old root is destroyed with its `ComponentDidDisappear`, `NativeScreenReleased` and `SlotDestroyed`, and
  the end steps and answer are the same. The layout's modals and overlays show after it, one by one, before the answer;
  its modals are made with the root and render unseen in the modals layer, held (`ModalCoordinator.hold`), so each
  shows in its turn without a wait of its own.
  The last `setRoot` wins: an older one is rejected (`ErrorTexts.setRootReplaced`) or finished at once.
- **`showModal`** (`Navigator.showModal`, `ModalCoordinator.show`). One `ModalRecord` per modal; it joins the modals
  layer unseen (`ModalViews`), waits with its own `RenderWait`, then slides in (or opens as a `ModalSheet`). It shows
  once every older waiting modal showed or went. Dismissing sends `ModalDismissed`; a layer a full-screen modal covers
  leaves the window.
- **`mergeOptions`** (`OptionsCommands`, no promise). Options are read on the module thread (`runVoid` with `prepare`).
  On main: `Navigator.findNode`, `ViewController.mergeOptions` (the own-options setter invalidates the cache; a shown
  node's presenter applies the merge; parents run `mergeChildOptions`, a hidden stack keeps the merge per entry), then
  `Navigator.optionsMerged` (modals, overlays, the system UI). No events, no state, no `CommandCompleted`.

## The single places

Each rule has one owner. Change the owner, not a caller; if a new feature needs the rule, call the owner.

- **Registry.** `ChildControllersRegistry`, one per `Navigator` (one per activity). Filled by `ChildController` when a
  node is made, emptied on destroy and by `Navigator.destroy`. `Navigator.findNode` hides nodes that go with an old
  root. `Navigators` holds the activities weakly; `Navigators.current()` is the one commands run in.
- **Factory.** `LayoutFactory`: one maker per `LayoutType`, `canMake` before a command makes anything. The only core
  code that names `SplitViewController` and `ArrangementController`.
- **Options cache.** `ViewController.resolveCurrentOptions(defaultOptions)` keeps its result while the generation of
  `OptionsCache` and the nodes it was built along stay. `OptionsCache.optionsChanged` is called from two places only:
  the `ViewController.options` setter and `DefaultOptions.set`. `resolveShownOptions` is what a layout shows with (the
  options of the end of its current-child chain; a pair answers with its `OptionsScreen`).
- **Appearance dispatcher.** `AppearanceDispatcher` is the only sender of `ComponentWillAppear`, `ComponentDidAppear`,
  `ComponentDidDisappear`, `ScreenPopped`, `ModalDismissed`, `NativeScreenReleased` and `SlotDestroyed`. Order:
  `beginTransition` starts the appearances; `endTransition` runs what left, then what came, then the state, then the
  transition's events, then the answer. Events asked before a screen's content is ready are held in its
  `ScreenAppearance` (until the content mounts or `HOLD_LIMIT_MILLIS`).
- **Navigation state.** `NavigationState` owns it: the last state sent, the walk (`stateOf`), the posted update and its
  holds (`NavigationState.Updates`). `AppearanceDispatcher.state` (`StateUpdates`) is the one way to ask for it:
  `setNeedsUpdate` coalesces the asks to one posted update per pass of the main loop and keeps it while held; the
  update calls `updateNow`. A command's answer sends it once, at once and never held (`CommandAnswer.reportStateNow`):
  at the end of its transition, else right before its promise for the commands that change what shows; nothing for a
  JavaScript that went. It reads the navigator through `Navigators.current()`, and `stateOf` finds the
  `tab` as the nearest tabs of the visible screen (the last tabs met walking down from the visible layout). A `setRoot`
  with layout modals or overlays takes `AppearanceDispatcher.StateUpdates.hold()` and releases it right before its
  answer, so JavaScript hears one state; every hold has one `release()`. The holds go when the JavaScript goes
  (`NavigationState.javaScriptWent`), so one left by its `setRoot` never holds the next JavaScript's updates.
- **Answer.** `CommandAnswer`: resolves or rejects once, and nothing once the JavaScript went. The id a stack entry
  answers with is one rule, `StackController.answerIdOf` (the queue asks it through `StackQueue.Host.answerIdOf` with
  an `AnswerKind`): `push` answers the entry's own id (a pair's), every other command the screen it stands for (a
  pair's primary).
- **Stack queue.** `StackQueue`: every command, back and return of a container goes through it; it keeps `entries`
  (what JavaScript sees) and `shown` (what the container shows) and answers once.
- **Screen readiness.** `ScreenReadiness` (by id; written by `SafeAreaView`) and `ContentReadiness` (the `Readiness`
  the waits ask, through `IReactView.isReady`). As on iOS, the signals are kept (a listener that comes later hears them
  at once), an id has a listener per key (several roots of one bar component), and each signal carries the React
  surface it came from: a `ReactView` takes only its own surface's, so an old root with the same id never makes a new
  one ready. An id renders at once while a root marks it (`ReactView`'s `rendersAtOnce`); the last one takes the id's
  signals with it. A screen's id is cleared as the screen is made and destroyed, everything on reload.
- **Render wait.** `RenderWait`: the one wait for screens to render, used by `RootCoordinator`, `ModalCoordinator`,
  `OverlayCoordinator` and `StackQueue`; `RenderWait.waitedScreens` walks `ParentController.waitedChildren()` at every
  level. What differs between users are values (limit, bar phase, unknown-content rule), never steps.
- **Waiting place.** A root that waits lies under the current root (`RootLayerViews.place`); a pushed screen that waits
  lies under the stack's top (`StackViews.placeWaiting`); a modal's and an overlay's container is transparent and
  gives its touches away while it waits (`ModalContainer.isWaiting`, `OverlayContainer.isWaiting`).
- **Waits to show.** `WaitingLayer`, kept by `Navigator` (`addWaitingLayer`, `removeWaitingLayer`, `isInWaitingLayer`).
  The root, a waiting modal and a waiting overlay register and leave on every exit. A stack inside a waiting layer runs
  its commands at once.
- **What a node shows changed.** `Navigator.shownScreensChanged(node)`: tells the waiting layer, the parent stack's
  queue (a pushed pair waits for its entry) and the modals' sheet. Called by `StackController`, `BottomTabsController`,
  `SideMenuController`, `SplitViewController` and `ArrangementController`. The system UI is told by the presenters
  (`SystemUiOwner.of(activity)` then `invalidate`).
- **Modal records.** `ModalCoordinator` and `ModalRecord` (states `Waiting`, `Presenting`, `Shown`, `Dismissing`,
  `ClosedBelow`, `WithOldRoot`, `Gone`). Nothing else keeps a list of modals. Android has no presentation gate:
  transitions overlap.
- **Overlays.** `OverlayCoordinator` and `OverlayRecord`; no animation, no part of the state; `OverlayTouches` decides
  which touches an overlay takes.
- **System UI owner.** `SystemUiOwner`, one per `Navigator`, started and released by the activity. Changes only mark it
  (`invalidate`); before the next frame it asks `SystemUiLayers` which layer decides each group, computes the state
  with `SystemUiRules` and writes the differences through `SystemUiWindow`, the only code that writes to the window.
  Without edge to edge it also writes the window's soft input adjust (`layout.adjustResize: false` of the top
  non-overlay layer: the keyboard covers the screen; else the manifest's mode).
- **Reload.** `ReloadCoordinator`: `generation` grows when the JavaScript goes (`ReactGateway` tells it);
  `Navigator.destroy` removes every layer silently and clears the registry; `AppLaunched` is sent once per pair of
  activity and React context. Stores are cleared with their nodes.
- **Back.** `NavigationActivity.passBack` → `ReactGateway.onBackPressed` (JavaScript's `BackHandler`) →
  `invokeDefaultOnBackPressed` → `Navigator.handleBack` → `ModalCoordinator.handleBack` → the root's
  `ParentController.handleBack` → `StackController.handleBack` → `StackQueue.cut`. `BackCallbackState` keeps the
  system callback enabled only while back has something to do, computed again on the events that change it (its KDoc
  lists them), never before each frame; debug builds check it (`BackCallbackCheck`).
- **Error texts.** `ErrorTexts`: one function per text, the same words as iOS, made with iOS's from
  `src/interfaces/contract/errors.json` (`scripts/contract.mjs`). JavaScript adds `[navigation] <command>:`.
- **Event names and keys.** `EventContract`, made from the event types of `src/interfaces/`; `EventEmitter` and the
  builders of payloads use it, never a string.
- **Room for the bars.** A screen's room at the top and bottom is `ScreenFrame` with `ScreenSafeArea` and the
  `ParentController` room hooks (`getTopInset`, `keepsStatusBarRoomOf`, …). A screen in a pair forwards its room to
  the pair (`ScreenFrame.applyRoom`), and the pair's `getTopInset` uses the screens' rule (`ScreenFrame.topRoomOf`)
  with the options it shows with. The bottom edge is not padded by default (only `'bottom'` in the edges pads it); `roomPadding` still shrinks the view for an opaque navigation bar and the keyboard. A view's place in the window for the safe area (the React root, the stack's header
  bottom in `headerBottomOf`, the tabs' top in `tabsTopOf`) is `RestPlace`: from layout, without translations, a sheet
  at rest.
- **A container's own move.** A container that moves children in or out of the window without a command (the split
  view's panes, the pair's screens) keeps its bookkeeping in `ShownChildren`, which sends the appearances through the
  dispatcher's order (`beginTransition`, then `endTransition`: what left, then what came).

## Modules

Modules are the feature classes that plug into the core through its contract (the interface of
`ARCHITECTURE.md` section 4). By area:

- **Containers:** split view (`SplitViewController`, `SplitViewLayout`, `SplitPanes`), pair (`ArrangementController`,
  `ArrangementLayout`, `ArrangementPlanner`).
- **Presenters and bars:** search (`StackSearchPresenter`, `ScreenSearch`, `SearchPanel`), menus (`ButtonMenus`), FAB
  (`Fab`, `FabMenu`), large title (`LargeTitleView`, `TopBarCollapseBehavior`), sheets (`ModalSheet`, `SheetOptions`),
  custom row (`BottomTabsCustomRowOptions`).
- **Views and helpers:** tabs bar (`BottomTabs`, `BottomTabItem`, `BottomTabsCollapseBehavior`, `scrollToTop`), slots
  (`SlotHost`, `SlotView`, `SlotContainers`), safe area (`SafeAreaView`), native screens (`nativescreen/`), splash
  (`splash/`), animations (`FadeThrough`, `SystemTransition`, `TransitionTargets`, three element animators), utils
  (`MaterialTheme`, `NavigationLog`, `awaitBarComponents`, `whenDrawn`).
- **Stores and singletons:** a screen's environment (`ScreenEnvironment`: its insets and padding, its regions as
  `ScreenRegions` measures them, its pair state), the window (`Hinge`, `WindowFolds`), links (`LaunchLink`,
  `StartInfo`), events (`NavigationState`), commands (`BackCallbackState`), stack (`PreparedFrame`).

How they plug in:

- **Containers** extend `ParentController` and give the core the children it asks for: `getChildControllers`,
  `getCurrentChild`, `visibleChildren()` (what appears with the layout; several for a side menu or two panes),
  `waitedChildren()` (what a wait for rendering waits for) and `resolveChildOptions` / `applyChildOptions` /
  `mergeChildOptions`. When what they show changes they call `Navigator.shownScreensChanged`; when children move in
  or out of the window by themselves they report through `ShownChildren`.
- **Capabilities** (`Capabilities.kt`) replace class checks; a node without one answers "no". `nodeWith<T>` finds the
  nearest node with it:
  `ReturnsScreenFromColumn` (a pop of a column's only screen goes back to the column before), `ScreensLeavingWithNode`
  (a pair takes both screens out of a stack; `ScreenPopped` for each), `ChildOffsetFromRest` (a moving child counts its
  safe area where it rests), `OptionsScreen` (the screen whose options are the node's), `KeepsStatusBarRoom` (marker:
  the node keeps the status bar's room, its screens none), `ShowsColumns` (`showInColumn`, `setColumnShown`).
- **Presenters** extend `Presenter` and override `applyOptions`, `mergeOptions`, `onConfigurationChanged`. The split view
  gets options from its module; the core's `SplitViewPresenter` only hands it the new defaults after
  `setDefaultOptions` (`setSplitOptions`).
- **Content** is `IReactView`: `ReactView` for a React screen, `SlotScreenView` for a name starting with `slot:`
  (`ScreenContentFactory`), and the view of a pair, which stands for its primary. The core asks only id, appearance
  record, readiness, mounted, stop and touches.
- **Core code in a module's folder:** `viewcontrollers/splitview/SplitViewPresenter.kt`, `slots/SlotScreenView.kt`,
  and in `views/animations/` and `views/element/` everything except the files named above.
- **Stores** are written on the main thread by the core and read from any thread; they are cleared with their nodes and on
  reload. A screen's environment is one immutable record per id in `ScreenEnvironment` (insets for JavaScript, padding,
  regions, pair state), replaced at once; the screen's node clears it with one `remove` when it is destroyed, unless a
  later node has its id; the padding listeners of its safe area views stay until the views stop. Their events, and
  the search's, go through the emitter of the JavaScript the screen was made for
  (`EventEmitter.of(node.jsGeneration)`); the hinge keeps the `EventEmitter.current()` of the call that turned it on.
