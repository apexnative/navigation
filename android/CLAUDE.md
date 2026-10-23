# Android core — map

The Android side of `@apexnative/navigation`: the native core that owns the navigation state of an app — stacks, tabs,
modals, overlays, side menus, split views and pairs — and is driven from JavaScript. Kotlin, package
`dev.apexnative.navigation`, views only (no Fragments), Material 3, log tag `ApexNavigation`. React Native 0.85+ with
the New Architecture only, minSdk 24, compileSdk 36, JDK 17. The options, the layout JSON, the commands and the events
are the contract of `src/interfaces/`; this folder implements it and does not change it. The JavaScript end is in
`src/ARCHITECTURE.md`.

Paths below are under `android/src/main/java/dev/apexnative/navigation/` unless they start with `android/` or another
top-level folder. Main thread only, unless a line says otherwise.

## Folders

- `NavigationApplication.kt`, `NavigationActivity.kt`, `NavigationPackage.kt`, `EdgeToEdgeDecision.kt`: the app-facing
  classes: the application (React host through `ReactGateway`, native screens by name), the activity (layers, splash
  hold, back, developer reload), the React package (`ANNTurboModule`, `ANNSlot`, `ANNSafeAreaView`), the edge-to-edge
  rule.
- `layout/`: `LayoutNode`, `LayoutType`, `LayoutReader` (module thread), `LayoutChecks`, `LayoutException`.
- `options/`: one class per option group (`TopBarOptions`, `StatusBarOptions`, …), `Options` (the root),
  `OptionsReader`, `OptionsCache`, `DefaultOptions`. `params/` has the value types (`Bool`, `Text`, `Fraction`,
  `Variant`, `ThemeColour`); `parsers/` reads them tolerantly from React Native data.
- `react/`: the way in and out: `NavigationTurboModule`, `CommandRunner`, `CommandAnswer`, `ErrorTexts`,
  `OptionsCommands`, `PendingCalls` (a task behind the calls JavaScript made), `ReloadCoordinator`, `ReactGateway`,
  `ReactView` (a React root), the stores (`ScreenInsets`, `ScreenRegions`, `ArrangementStates`, `Hinge`, `StartInfo`),
  `LaunchLink`, `ScrollEvents`.
- `react/events/`: `AppearanceDispatcher`, `ScreenAppearance`, `EventEmitter`, `EventContract` (made by
  `scripts/contract.mjs`), `NavigationStateReporter`, `ComponentType`.
- `viewcontrollers/viewcontroller/`: `ViewController` (the base of every node), `Presenter`, `IReactView` (what a
  screen shows), `ScrollEventListener`.
- `viewcontrollers/child/`, `parent/`: `ChildController` and the registry; `ParentController` (the base of layouts),
  `Capabilities.kt`, `ShownChildren`, `ContainerBackground`.
- `viewcontrollers/navigator/`: `Navigator`, `Navigators`, `LayoutFactory`, `RootCoordinator`, `WaitingLayer`,
  `FirstRootDraw`, `BackCallbackState`.
- `viewcontrollers/component/`: the React screen: `ComponentViewController`, `ComponentPresenter`, `ScreenFrame`,
  `ScreenSafeArea`, `RestPlace`, `ScreenReadiness`, `ContentReadiness`, `RenderWait`, `ScreenContentFactory`,
  `ScreenFocus`.
- `viewcontrollers/externalcomponent/`: the native screen node (`ExternalComponentViewController`) and
  `NativeScreens`.
- `viewcontrollers/stack/`: `StackController`, `StackQueue`, `StackCommands`, `StackViews`, `StackDisplay`,
  `StackTransitions`, `PreparedPushes`. `fab/`, `search/`, `topbar/` hold the bar, the FAB and the search presenters.
- `viewcontrollers/bottomtabs/`: `BottomTabsController`, `BottomTabsPresenter` and the pure rules (`TabSelection`,
  `TabsAttach`, `TabsRoom`, `TabBarLook`, `TabBarMotion`, `TabItems`).
- `viewcontrollers/sidemenu/`: `SideMenuController`, `SideMenuMotion`, `SideMenuPresenter`, `SideMenuRules`.
- `viewcontrollers/splitview/`, `arrangement/`: the split view and the pair (modules, plus the core's
  `SplitViewPresenter`).
- `viewcontrollers/modal/`, `overlay/`: `ModalCoordinator`, `ModalRecord`, `ModalViews`, `ModalSheet`;
  `OverlayCoordinator`, `OverlayRecord`, `OverlayViews`, `OverlayTouches`.
- `views/`: Android views and animators, no rules: `component/`, `externalcomponent/`, `stack/` (`StackLayout`,
  `topbar/`, `fab/`), `bottomtabs/`, `sidemenu/`, `animations/` (curves, `SideAnimator`, `AnimationRules`), `element/`
  (element and shared-element transitions).
- `system/`: the system UI: `SystemUiOwner`, `SystemUiLayers`, `SystemUiRules`, `SystemUiState`, `SystemUiWindow`,
  `AppDirection`.
- `slots/`, `safearea/`, `nativescreen/`: the views of `ANNSlot` and `ANNSafeAreaView`, the slot screen, the
  native-screen context and calls.
- `splash/`, `customrow/`, `utils/`: the splash hold, theme and copy; the tabs' custom row options; helpers
  (`UiThread`, `ImageLoader` with `OptionImages`, `ImageKey`, `ImageRequests`, `MaterialTheme`, `TestIds`, `WindowFolds`,
  `DevelopmentLog`).
- `android/src/main/jni/`: `CMakeLists.txt` builds the codegen target with the C++ of `ios/cpp` (the slot's and the
  safe area view's shadow nodes); `ApexNavigationSpec.h` replaces the generated header so autolinking sees their
  descriptors.
- `android/src/main/res/`: theme `Theme.ApexNavigation` (and `values-night`), `attrs.xml` (splash attributes),
  `ids.xml`, `dimens*.xml`, text colors, icons.
- `android/src/test/java/…`: unit tests, one package per main package; fakes beside them (`Fakes.kt`,
  `StackFakes.kt`, …).

## Where to look

- Launch and the app-facing API: `NavigationApplication.kt`, `NavigationActivity.kt`, `react/ReactGateway.kt`,
  `NavigationPackage.kt`, `EdgeToEdgeDecision.kt`, `react/ReloadCoordinator.kt` (`AppLaunched`),
  `react/DeveloperReload.kt`.
- Root (`setRoot`, splash, reload): `viewcontrollers/navigator/RootCoordinator.kt`, `FirstRootDraw.kt`, `Navigator.kt`;
  `splash/SplashHold.kt`, `SplashTheme.kt`, `SplashView.kt`; `react/ReloadCoordinator.kt`.
- Screen (React root, slot, native, insets, background, keyboard): `viewcontrollers/component/ComponentViewController.kt`,
  `ScreenFrame.kt` (bars' room, window insets, keyboard), `ScreenSafeArea.kt`, `ComponentPresenter.kt` (background),
  `ScreenContentFactory.kt`, `ScreenFocus.kt`; `react/ReactView.kt`; `views/component/ComponentLayout.kt`.
- Stack (queue, commands, back): `viewcontrollers/stack/StackQueue.kt`, `StackCommands.kt`, `StackController.kt`,
  `StackViews.kt`, `StackTransitions.kt`, `PreparedPushes.kt`; `views/stack/StackLayout.kt`, `ScreenEdge.kt` (the edge
  a moving screen extends); back: `NavigationActivity.kt`, `viewcontrollers/navigator/BackCallbackState.kt`; the
  preview of a pop during the back gesture (Android 14+): `StackController.BackGesture`, `BackPreview.kt`,
  `stackPoppedByBack` of the layouts.
- Stack FAB: `viewcontrollers/stack/fab/FabPresenter.kt`, `FabPlan.kt`, `FabPlacement.kt`; `views/stack/fab/`.
- Top bar (title, buttons, menus, large title): `viewcontrollers/stack/topbar/TopBarPresenter.kt`, `TopBarRoom.kt`,
  `TopBarVisibility.kt`, `BarComponents.kt`, `ButtonPlaces.kt`, `LargeTitleCollapse.kt`, `BarRowTransition.kt`;
  `views/stack/topbar/TopBar.kt`, `TitleBar.kt`, `titlebar/ButtonBar.kt`, `titlebar/ButtonMenus.kt`.
- Search: `viewcontrollers/stack/search/StackSearchPresenter.kt`, `ScreenSearch.kt`, `SearchPanel.kt`;
  `views/stack/topbar/search/`.
- Tabs: `viewcontrollers/bottomtabs/BottomTabsController.kt`, `BottomTabsPresenter.kt`, `TabSelection.kt`,
  `TabItems.kt`, `TabsRoom.kt`; `views/bottomtabs/`; `customrow/BottomTabsCustomRowOptions.kt`,
  `viewcontrollers/bottomtabs/CustomTabRowPlacer.kt`.
- Modals and sheets: `viewcontrollers/modal/ModalCoordinator.kt`, `ModalRecord.kt`, `ModalViews.kt`, `ModalSheet.kt`,
  `ModalAnimations.kt`; `options/SheetOptions.kt`.
- Overlays: `viewcontrollers/overlay/OverlayCoordinator.kt`, `OverlayViews.kt`, `OverlayTouches.kt`.
- Side menu: `viewcontrollers/sidemenu/SideMenuController.kt`, `SideMenuMotion.kt`, `SideMenuRules.kt`,
  `SideMenuPresenter.kt`; `views/sidemenu/SideMenuView.kt`.
- Split view: `viewcontrollers/splitview/SplitViewController.kt`, `SplitViewLayout.kt`, `SplitPanes.kt`,
  `SplitViewPresenter.kt`; `StackCommands.showInColumn`.
- Pair (arrangement): `viewcontrollers/arrangement/ArrangementController.kt`, `ArrangementPlanner.kt`,
  `ArrangementLayout.kt`, `ArrangementDefinition.kt`; `react/ArrangementStates.kt`.
- Preview, key commands: iOS only. The options are parsed and merged (`options/PreviewOptions.kt`,
  `options/KeyCommandOptions.kt`) and not applied; `dismissPreview` does nothing.
- Animations and transitions: `views/animations/` (`AnimationRules`, `Curves`, `SideAnimator`, `ValueInterpolation`,
  `FadeThrough`, `SystemTransition`); `views/element/` (`ElementTransitions`, `TransitionLayer`, `TargetsWait`,
  `animators/`); `options/StackAnimationOptions.kt`, `ViewAnimationOptions.kt`.
- System bars: `system/SystemUiOwner.kt`, `SystemUiLayers.kt`, `SystemUiRules.kt`, `SystemUiWindow.kt`,
  `AppDirection.kt`; `viewcontrollers/parent/ContainerBackground.kt`.
- Events and state: `react/events/AppearanceDispatcher.kt`, `ScreenAppearance.kt`, `EventEmitter.kt`,
  `NavigationStateReporter.kt`; `react/CommandAnswer.kt`.
- Options (parsing, merging): `options/Options.kt`, `OptionsReader.kt`, `OptionsCache.kt`, `DefaultOptions.kt`,
  `params/`, `parsers/`; `viewcontrollers/viewcontroller/ViewController.kt` (resolution).
- Error texts and answers: `react/ErrorTexts.kt` and `react/CommandContract.kt`, made by `scripts/contract.mjs` from
  `src/interfaces/contract/errors.json` and `commands.json`.
- Links: `react/LaunchLink.kt`, `NavigationActivity.kt` (`onNewIntent`); `react/StartInfo.kt`.
- Hinge and regions: `react/Hinge.kt`, `react/ScreenRegions.kt`, `react/ScreenInsets.kt`, `utils/WindowFolds.kt`.
- Native screens: `nativescreen/NativeScreenContext.kt`, `NativeScreenCalls.kt`, `NativeScreenCreator.kt`;
  `viewcontrollers/externalcomponent/`.
- Slots: `slots/SlotHost.kt`, `SlotView.kt`, `SlotScreenView.kt`, `SlotContainers.kt` (containers and slot views by
  slot id: a change goes straight to the views of its id, `SlotViews.kt`), `SlotViewManager.kt`.
- Safe area: `safearea/SafeAreaView.kt`, `SafeAreaViewManager.kt`; `viewcontrollers/component/ScreenReadiness.kt`;
  `react/ScreenInsets.kt`.
- The C++ of `ANNSlot`, `ANNSafeAreaView`: `android/src/main/jni/CMakeLists.txt`, `ApexNavigationSpec.h`; sources in
  `ios/cpp/`.

## Threads

- **JS thread.** The sync getters `getScreenInsets`, `getScreenRegions`, `getArrangementState`, `getHinge`,
  `getStartInfo` read stores (`ConcurrentHashMap` in `ScreenInsets`, `ScreenRegions`, `ArrangementStates`; `@Volatile`
  in `Hinge`). They never touch a node.
- **Module thread.** The `check` of every command with a promise: `LayoutReader` and, through it, `OptionsReader` (also
  for `setDefaultOptions`, `mergeOptions` and the options of `dismissModal`, in the runner's `prepare` step);
  `ReadableJson` for native-screen props. The registry of native screens is read under its lock. Images are not loaded
  here; they load when the options apply, through the one cache of `utils/OptionImages` (by `ImageKey`): at once on
  main where the first frame needs them (`ImageLoader`: the top bar's buttons, menus, back button and search
  suggestions; a tab's first icon; a new FAB), on its background thread for a file or a drawable of the app where the
  view takes them when ready (`ImageRequests`: a changed tab icon or one that didn't load, a FAB's changed icon, the
  icon of a FAB hidden with `visible: false`, the FAB menu's actions).
- **Main thread.** Everything else: the command body, nodes, views, events, the stores' writes. Any thread may read
  `ReloadCoordinator.generation`.

## Checks

- `scripts/build-android.sh --lib` builds the library alone; `scripts/build-android.sh` builds the Showcase (arm64);
  with a device serial it installs and starts it. Kotlin and Java warnings are errors
  (`-PapexNavigationWarningsAsErrors`).
- `scripts/test-android.sh [--tests '*SomeTest']` runs `:apexnative_navigation:testDebugUnitTest` from `example/android`.
  The tests are plain JUnit 4 on the JVM (`unitTests.returnDefaultValues`, no Robolectric), in
  `android/src/test/java/dev/apexnative/navigation/`, one package per main package. Coordinators and rules are written
  to run with fakes (`StackFakes.kt`, `ModalFakes.kt`, `AppearanceFakes.kt`, `Nodes.kt`); a node that touches views in
  its constructor can't be tested this way.
- Gradle runs only through `scripts/android-env.sh` (JDK 17); the scripts do it.
- A change in `ios/cpp/` is built on both platforms.
- `scripts/check.sh` covers the TypeScript side only.

## Pitfalls

- Native code never touches React Native's views (their children, their layout): Fabric owns and recycles them. Move
  content through `SlotContainers` and `SlotView`, and find views by `nativeID` only through React Native's own search
  or its tag (`ReactR.id.view_tag_native_id`); a transition's elements in one walk per screen (`TransitionViews`).
- Each step of a transition's element (its move into the layer, its animator, a flight's start and frames in
  `MoveAnimator`) runs in `elementStep`: an exception skips that element only. A new step of an element goes there
  too, or one bad element takes the whole transition down.
- Every change of a stack's children goes through its `StackQueue`; a direct change of `shown` or of the container
  skips the table that decides interrupts and answers.
- Every event of an appearance goes through `AppearanceDispatcher`. A container with its own loop of
  `onViewWillAppear` / `onViewDidAppear` / `onViewDisappear` sends the events in the wrong order.
- Never change a resolved options object: it is cached and shared. Copy it, or write through `changeOptions`; a
  user's choice goes through `changeChoices` (the user layer), never into own options, or the next `mergeOptions`
  wipes it.
- A new option field that is not in `mergeWith`, `mergeWithDefault`, `copy` or the fixture is dropped silently somewhere
  in the chain; `WholeTreeTest` exists to catch it.
- A new wait is a new `RenderWait` value, not a new timer. A wait that survives a destroy or a supersede hangs a promise;
  `cancel` it on every exit.
- A layer that waits to show must be a `WaitingLayer` and leave on every exit (show, supersede, destroy, root gone).
- Never resolve a promise yourself: only `CommandAnswer` knows whether its JavaScript went and sends the state first.
- A signal that comes inside a layout pass or a view's attach must not move views there; the stack posts readiness
  (`deliver`).
- A step that runs later (a timer, a readiness signal, an animator's end, a user callback) runs under no runner: it
  goes through its owner's guard (`StackQueue.step`, `RootCoordinator.step`, `ModalCoordinator.guarded`,
  `OverlayCoordinator.guarded`), which rejects the open answer and ends the record, or an exception ends the app and
  the command never answers.
- A node makes its view in `createView`, never in its constructor.
- Whether a node keeps the status bar's room (its margin, its background band) goes through
  `StatusBarOptions.isHiddenOrDrawBehind(node.isInOverlay)`: an overlay covers the whole window, and a rule that reads
  `drawBehind` alone puts a light band over an overlay's dimmed backdrop.
- Only `SystemUiWindow` writes to the window. A presenter that changes a bar option calls `SystemUiOwner.invalidate`.
- The core names a module container only in `LayoutFactory`; elsewhere use a capability or `visibleChildren()`.
- A hidden stack keeps a `mergeOptions` per entry; applying it for the wrong entry paints the wrong screen's bar.
- A `mergeOptions` moves or resizes only by what the merge names: the resolved options still hold the values of the
  show, and acting on them undoes what the user did since (a dragged sheet snapped back to `selectedDetent`).
- A sync getter reads a store, never a node; the store is written on main and cleared with its node.
- A re-apply of the large title (a pop back, a light/dark switch, `mergeOptions`) collapses it to the list's last
  offset that `ScrollEventListener.lastOffset` keeps, never to 0, or it comes back expanded over a scrolled list.
- A view's place in the window while a modal slides or a sheet is dragged comes from layout (`RestPlace`), never from
  `getLocationInWindow`, which counts the translations of the animation.
- The bar measures to its children: a picture laid into it for a transition that is taller than the bar the incoming
  screen gives holds the bar at that height to the end (the row's picture of a pop from a large title). What doesn't
  stay in the bar rides with the leaving screen (`TopBarPresenter.pictureUnderRow`).
- A `Toolbar` centers its navigation button and its menu in `min(minimumHeight, height)` (`buttonGravity` is top):
  the row's toolbars take the row's height as their minimum (`TitleBar`); 0 pins the buttons to the row's top.
- A screen's overlay is clipped to the screen, and a screen without a large title is laid out under the bar (its `top`
  is the bar's height): a picture of the bar in its overlay doesn't show. The bar's picture in a transition of the
  stack's own is drawn by the stack's view with the screen's transform (`StackLayout.barTravels`).
- A React screen without `layout.backgroundColor` is opaque with the theme's `android:colorBackground`
  (`ComponentPresenter`): a push keeps the covered screen under the new one until it ends, and a transparent screen
  shows it. Only an explicit `'transparent'` / `null` (overlays, see-through modals) lets it through.
- The back gesture's preview shows the screen under the top outside any operation of the queue: it may only reveal
  it and put it back, and its release goes through `StackQueue.cut(gesture = true)`. A command that comes meanwhile
  stops it through `StackDisplay.stop`, like any transition it takes over.
- Without edge to edge the window resizes for the keyboard by the manifest's soft input mode, so
  `layout.adjustResize: false` goes through the system UI owner's keyboard group (`SystemUiWindow.setKeyboardCovers`),
  not through the screen's padding.

## Read when needed

- `android/CORE.md`: the layers, how a command flows (`push`, `setRoot`, `showModal`, `mergeOptions`), the single
  places (which class owns which rule) and the modules. Read it when changing the core's structure, adding a module or
  tracing a flow.
- `android/RECIPES.md`: the steps for adding an option and for adding a command. Read it when doing exactly that.
