# iOS core

## What is here

The iOS side of the navigation: Objective-C++, the pod and SwiftPM target `ApexNavigation`, class prefix `ANN`, log
prefix `[ApexNavigation]`. iOS 15.1 and later, iPhone and iPad; React Native 0.85 and later
with the New Architecture only (Fabric, bridgeless). Newer-OS features sit behind `@available` and, for names of an
SDK newer than the one the app builds with, `#if __IPHONE_OS_VERSION_MAX_ALLOWED >= …` (the pair needs the iOS 27.1
SDK). The native side owns the navigation: stacks, tabs, modals, overlays, side menus, split views and pairs are UIKit
view controllers that JS builds from a layout and drives with commands. The options, layouts, commands and events are
the contract of the types in `src/interfaces/`; this folder implements it.
Swift is not used here: apps reach the core through the public headers (`Public/ApexNavigation/`).

## Folders

- `TurboModules/`: `ANNTurboModule` (the commands and the sync getters), `ANNTurboEventEmitter` (the events module).
- `Contract/`: The command path: runner, answer, layout reader and nodes, validator, error texts, tree options.
- `Core/`: The spine: node protocol and helper, registry, dispatcher, state, render wait, parking, system UI, reload, event emitter, start (`ApexNavigation.mm`, `ANNAppDelegate.mm`), and the modules `ANNKeyCommands`, `ANNDeepLinkQueue`, `ANNHinge`, `ANNSceneDelegate.mm`.
- `Layouts/Root/`: `ANNRootCoordinator` (`setRoot`), `ANNLayoutFactory`, `ANNSplashViewController`.
- `Layouts/Screen/`: The screen (`ANNComponentViewController`), its presenter, readiness, insets, regions, safe-area math, the main list rule, previews.
- `Layouts/External/`: Native screens: `ANNExternalViewController`, `ANNNativeScreenCalls`.
- `Layouts/Stack/`: `ANNStackController`, `ANNStackQueue`, `ANNStackCommands`, `ANNStackPresenter`, `ANNPreviewPush`, the swipe-back delegate.
- `Layouts/Tabs/`: `ANNBottomTabsController`, its presenter, item builder, custom row, preload window, More delegate, long press, dots.
- `Layouts/Modal/`: `ANNModalCoordinator`, `ANNModal`, `ANNPresentationGate`, `ANNModalPresentation`, `ANNModalTransitions`, sheets and popovers.
- `Layouts/Overlay/`: `ANNOverlayCoordinator`, `ANNOverlayWindow`.
- `Layouts/SideMenu/`: The side menu module and its presenter and wrapper.
- `Layouts/SplitView/`: The split view module, plus the core's `ANNSplitViewPresenter` and `ANNSplitViewCommands`.
- `Layouts/Arrangement/`: The pair (iOS 27.1): `ANNArrangementViewController`, `ANNArrangementState`.
- `TopBar/`: The bar of a screen: title, buttons, back button, large title, appearance, search, toolbar, edge effects.
- `Options/`, `Options/Params`: One `ANN<Group>Options` class per group, the field table, the value kinds and parsers (`Bool`, `Color`, `Text`, `Image`, …), the images' cache and loader (`ANNOptionImages`).
- `Animations/`: The custom transition engine: plan, animator, wait, frame driver, rules; `Elements/` and `Interpolators/`.
- `React/`, `React/Slots/`: React views of screens and components, the surface host, the safe area view, the slot host and slot screens.
- `Public/ApexNavigation/`: The headers apps import: `ApexNavigation.h`, `ANNAppDelegate.h`, `ANNSceneDelegate.h`.
- `Utils/`: Two small categories: `UIColor+ANNLuminance`, `UIView+Utils`.
- `cpp/`: The C++ of the Fabric components `ANNSlot` and `ANNSafeAreaView` (props, state, shadow nodes, descriptors). Android builds the same files.

Top-level files: `Package.swift` (SwiftPM; `headerFolders` lists every folder whose headers are imported by bare name),
`ApexNavigation-prefix.h` (SwiftPM only). The pod is `ApexNavigation.podspec` at the repository root.

## Where to look

- Launch, the app-facing API — `Public/ApexNavigation/`; `Core/`: `ApexNavigation.mm`, `ANNAppDelegate.mm`, `ANNSceneDelegate.mm`, `ApexNavigation+Internal.h`
- Root, splash, reload — `Layouts/Root/`: `ANNRootCoordinator`, `ANNSplashViewController`; `Core/`: `ANNReloadCoordinator`
- Screen: React root, slot, native screen — `Layouts/Screen/`: `ANNComponentViewController`; `React/`: `ANNReactView`, `ANNReactViewCreator`, `Slots/`; `Layouts/External/`
- Screen: insets, background, keyboard focus — `Layouts/Screen/`: `ANNScreenInsets`, `ANNScreenSafeArea`, `ANNScreenBars`, `ANNScreenPlace` (the safe area of a parked screen), `ANNComponentPresenter` (background, status bar blur), `ANNComponentViewController` (key commands)
- Safe area view — `React/`: `ANNSafeAreaViewComponentView`; `Layouts/Screen/`: `ANNScreenReadiness`; `cpp/`
- Stack: queue, commands, back, gestures — `Layouts/Stack/`: `ANNStackQueue`, `ANNStackCommands`, `ANNStackController`, `InteractivePopGestureDelegate`, `ANNStackAnimatingDelegate`
- Top bar: title, buttons, menus, large title — `TopBar/`: `ANNTopBarPresenter`, `ANNBarButtons`, `ANNBarAppearance`, `ANNLargeTitlePresenter`, `ANNBackButtonPresenter`, `ANNTitleView`, `ANNTitleGlassView`
- Top bar: components, waits — `TopBar/`: `ANNBarComponentsWait`, `ANNBarBackgroundComponent`, `ANNTitleComponentView`, `ANNBarButtonComponentView`; `React/`: `ANNReactComponentRegistry`
- Search — `TopBar/`: `ANNSearchPresenter`, `ANNSearchResultsViewController`
- Toolbar — `TopBar/`: `ANNToolbarPresenter`; `Options/`: `ANNToolbarOptions`; `Layouts/Stack/`: `ANNStackPresenter`
- Tabs — `Layouts/Tabs/`: `ANNBottomTabsController`, `ANNBottomTabsPresenter`, `ANNBottomTabItemBuilder`, `ANNBottomTabsCustomRow`
- Modals, sheets, popovers — `Layouts/Modal/`
- Overlays — `Layouts/Overlay/`
- Side menu — `Layouts/SideMenu/`
- Split view — `Layouts/SplitView/`
- Pair (arrangement) — `Layouts/Arrangement/`
- Preview — `Layouts/Stack/`: `ANNPreviewPush`; `Layouts/Screen/`: `ANNPreviewInteraction`
- Animations, transitions — `Animations/` (plan and animator at the top; `Elements/` for shared elements and zoom; `Interpolators/` for curves and tracks)
- System bars, orientation, direction — `Core/`: `ANNSystemUI`, `ANNSystemUIRules`, `ANNAppDirection`, `ANNNodePresenter` (the answers to UIKit)
- Events and state — `Core/`: `ANNEventEmitter`, `ANNEventContract` (names and keys, made by `scripts/contract.mjs`), `ANNAppearanceDispatcher`, `ANNScreenAppearance`, `ANNNavigationState`; `TurboModules/`: `ANNTurboEventEmitter`
- Options: parsing, merging, defaults — `Options/`: `ANNOptions`, `ANNOptionField`, `ANNOptionsReader`, `ANNDefaultOptions`, `ANNOptionsCache`; `Core/`: `ANNOptionsRepaint`
- Error texts and answers — `Contract/`: `ANNErrorTexts` and `ANNCommandContract` (made from `src/interfaces/contract/errors.json` and `commands.json` by `scripts/contract.mjs`); `Core/`: `ANNErrorHandler`
- Links — `Core/`: `ANNDeepLinkQueue`; the `getLaunchLink` method of `ANNTurboModule`
- Key commands — `Core/`: `ANNKeyCommands`; `Layouts/Screen/`: `ANNComponentViewController`
- Hinge, regions — `Core/`: `ANNHinge`; `Layouts/Screen/`: `ANNScreenRegions`; `Layouts/Arrangement/`: `ANNArrangementState`
- Native screens — `Layouts/External/`: `ANNExternalViewController`, `ANNNativeScreenCalls`; `Layouts/Screen/ANNNativeScreenContext.mm`
- Slots — `React/Slots/`: `ANNSlotHost`, `ANNSlotContainers` (containers and slot views by slot id: a change goes
  straight to the views of its id), `ANNSlotScreenView`, `ANNSlotComponentView`; `cpp/`

## Threads

- **Main queue:** everything but the sync getters and the images. `ANNTurboModule`'s method queue is the main queue,
  as `ANNTurboEventEmitter`'s: an async method comes there from the JavaScript thread, and `ANNCommandRunner` runs the
  layout check (`ANNLayoutReader`) and the body in that one task. A command body starts with `ANNAssertMainQueue()`.
  The options of a layout are parsed here (`ANNLayoutTreeOptions`; an image keeps only its source). `ANNEventEmitter`
  moves an event from another queue to the main queue.
- **The images' queue** (`ANNOptionImages`, `Options/Params`): every option image decodes there, through one cache.
  An element that shows an image asks for it when it applies and waits for it (the first frame needs it); only a tab
  item's new icon on the bar that shows comes later (`ANNBottomTabsPresenter`), the last build of the item winning.
- **Sync getters** answer JS on the JavaScript thread (React Native calls them there whatever the module's queue)
  during a screen's first render: `getScreenInsets`, `getScreenRegions`, `getArrangementState`, `getHinge`,
  `getStartInfo`. They read `ANNScreenInsets`, `ANNScreenRegions`, `ANNArrangementState`, `ANNHinge lastValue`,
  written on the main queue and guarded by `@synchronized`.
  `ANNReloadCoordinator.generation` and `ANNEventEmitter.isCurrent` are readable from any thread.
- **After the calls of JavaScript:** `ANNJavaScriptCalls afterCallsMadeSoFar:` runs a step on the main queue after
  every call JavaScript made by then, by the calls' own way (a task on the JavaScript thread, then one on the main
  queue). A push's wait ends through it, so the `mergeOptions` of its screen's first commit come first.

## Checks

- `scripts/build-ios.sh`: 0 warnings in `ios/` (warnings fail it). It builds the Showcase for the simulator.
- A new file needs `scripts/pods.sh` first (CocoaPods globs `ios/**`). A new folder also goes into `headerFolders` of
  `Package.swift`, because files import each other's headers by bare name.
- There is no iOS unit-test target. `ANNSystemUIRulesSelfCheck` and `ANNCheckOptionClassesFrom` run once in DEBUG; the
  rest is checked by the Showcase scenarios.
- Format by hand: `.claude/rules/code-objc.md`.

## Pitfalls

- Native code never touches React Native's views (their subviews, their layout, their frames): Fabric owns and recycles
  them. Read them; what a transition writes (`alpha`, `transform`, a hidden flag) goes back on every path.
- A screen parked by a wait for its content (`ANNParking`) has no parent, and UIKit gives its view no safe area: it lays
  out with the safe area of its place (`ANNScreenPlace`, measured on a navigation controller of the core's), or its
  list would move as the push starts and UIKit would show the large title only after the transition. On iOS 26 its
  large title also keeps a place of the core's for the push (`arrivesByPushOn:`). Check Showcase 1.4 (with a font
  size), every section panel and 12.6 on push, pop and swipe after any change to waits, parking or the large title.
- Every change of a stack's children goes through its `ANNStackQueue`. The core's own calls to UIKit's navigation
  come only from there; UIKit's own pops are followed (`uikitShows:`), not fought.
- Every UIKit present and dismiss in the main window is a step in `ANNPresentationGate`. UIKit drops a transition
  asked for while another runs, and never calls its completion.
- Only `ANNAppearanceDispatcher` sends the appearance events. A screen's UIKit callbacks and a command's own calls
  both reach it; whichever comes second finds the phase set. While a command drives a node, UIKit's callbacks send
  nothing.
- Touching `view` of a screen loads it, and `viewDidLoad` renders it. Use `isViewLoaded` or `viewIfLoaded` where a
  render is not wanted (a parked or pending node).
- Own options and the defaults are never changed in place. Resolved options are cached and read-only: copy before
  changing.
- Take a node's parent with `layoutParent`, not `parentViewController`: UIKit moves screens (More, a split view's
  columns, a pair's screen out of the window).
- A block that runs later keeps the emitter and the generation it was made with. After a reload the answer and the
  events of the old JavaScript are silent; a node never asks `ApexNavigation.eventEmitter` at event time.
- A wait sets its ready handler under a key of its own (`setReadyHandler:forKey:`) and removes only that one. A wait is
  about `waitedComponentViewControllers`, not about the shown screens.
- A container that changes what it shows calls `+[ANNShownScreens changedIn:]` and nothing else of its own choice.
  A change UIKit makes by itself (a collapsed split view's back) is followed from the revealed screen's
  `descendantDidAppear:`.
- Hold and release of the navigation state come in pairs; a `setRoot` chain with layout modals or overlays holds it
  until its answer.
- UIKit draws a dismissal without animation too (a sheet's dim goes a frame before its card): what goes out of sight
  that way goes under a snapshot of the window (the old root's modals at `setRoot`, `startTransitionOf:done:`).
- Use an `@available` check for newer OS APIs, and `#if __IPHONE_OS_VERSION_MAX_ALLOWED >= …` for names of a newer SDK.
- React Native hears of the app's light or dark style only from its hosting views in the window. A view that stands in
  for one (`ANNSlotScreenView`, the screens of one tree) posts the trait change itself, with the window filter of
  `ANNSurfaceHostingView`; without it `useColorScheme` keeps the old theme.
- A step that runs later in a command (a ready handler, a timer, a transition's end) goes through the owner's guard
  (`protect:step:`), so an exception rejects the command instead of crashing the app. So does the start of a wait
  (the root, a modal, an overlay: their record is listed and parked by then), and the modal's end steps inside UIKit's
  completions and delegate calls (`protectEndOf:showing:step:`, `protectBatch:step:`); the overlay's guard is
  `protectWaitOf:step:`.
- Each step of a transition's element (a pair's start and take-off, an element's start, every frame and restore
  through `ANNIsolatedElement`) runs in `ANNElementStep`: an exception skips that element only. A new step of an
  element goes there too, or one bad element stops the transition (a frame's exception ends the app). The elements of
  a transition are found in one walk per screen (`ANNTransitionViews`).
- An `ANNCommandAnswer` that goes away unanswered rejects (`notAnswered:`), but only once nothing holds it: a record,
  list or queue that keeps an answer still answers it on every exit, or the promise waits as long as the record lives.
- On iOS 26 UIKit draws a screen's large title, for a whole pop or swipe back, inside the scroll view the bar follows
  for the top edge: a detached scroll view or a list below the bar hides it. `ANNLargeTitlePresenter` lays its title
  host over the screen's view for the transition and fills the band behind the title from the screen's top
  (`ANNStackController` `popReveals:leaving:on:`); keep both when changing the edge scroll view or the bar's look.
- Under a hidden bar UIKit's swipe delegate refuses the swipes in questions of its own: `InteractivePopGestureDelegate`
  stops passing those on while the bar is hidden (`barHidden`, set again on the recognizer at each change, from
  `swipeBackFollowsBar`), and the core sorts the pans itself.
- UIKit works out the safe area of the window's root view from where it is drawn: a `transform` that moves it (a
  root's own `enter`) leaves it with no top inset, and putting the transform back doesn't work it out again. Lay the
  root out after a restore (`+[ANNRootCoordinator stopAnimationOf:]`).
- UIKit's `hidesBarsWhenKeyboardAppears`, once on, keeps hiding the bar after it is set back to `NO`: the stack hides
  the bars for `topBar.hideOnKeyboard` itself (`keyboardWillShow:` / `keyboardDidHide:` of `ANNStackController`) and
  leaves the flag off.
- A container that stands in a stack or a tab where a screen can (a pair, a split view, a side menu) sets
  `extendedLayoutIncludesOpaqueBars`, as a screen does: otherwise UIKit takes an opaque bar's height off its view, and
  a tab bar in iPhone Duo's vertical strip is as tall as the window, so the container gets no height.
- A tab opened from More shows its screens on UIKit's More navigation: the swipe delegates
  (`installSwipeBackDelegatesOn:`), the keyboard's bar (`keyboardDidHide:`) and the direction
  (`navigationMissedSceneDirection:`) go there too; read `shownNavigationController`, not the stack itself.

## Read when needed

- `ios/CORE.md`: the layers, how a command flows, the single places that own one rule each, the modules. Read it when
  you change the core's structure, add a module or trace a flow.
- `ios/RECIPES.md`: the steps for adding an option and for adding a command. Read it when you do exactly that.
- `ARCHITECTURE.md` (repository root): the design both platforms follow.
