# iOS core: structure

The structure of the iOS core: its layers, the path of a command through them, the single places that own one
rule, and how modules plug in. Read it when you change the core, add a module or trace a flow; the map of folders
and files is `ios/CLAUDE.md`.

## Layers

```
JS ── ANNTurboModule (codegen ApexNavigationSpec) ────────────────────────────────────────────┐ sync getters
       │ module queue                                                                          │ read stores
       ▼                                                                                       ▼ (@synchronized)
  ANNCommandRunner ── ANNLayoutReader (check the whole layout) ── ANNErrorTexts ── ANNErrorHandler
       │ one task per command on the main queue, in call order
       ▼
  area command (ANNStackCommands, ANNRootCoordinator, ANNModalCoordinator, ANNOverlayCoordinator, ANNSplitViewCommands)
       │ ANNLayoutValidator · ANNLayoutTreeOptions · ANNLayoutFactory ── ANNNodeRegistry
       ▼
  nodes: ANNComponentViewController · ANNExternalViewController · ANNStackController · ANNBottomTabsController ·
         ANNSideMenuViewController · ANNSplitViewController · ANNArrangementViewController   (ANNLayoutProtocol)
       │ options: ANNOptionsReader → ANNNavigationOptions; ANNNodeHelper resolves and caches (ANNOptionsCache)
       ▼
  coordinators: ANNRootCoordinator · ANNStackQueue (per stack) · ANNModalCoordinator + ANNPresentationGate ·
                ANNOverlayCoordinator · ANNRenderWait · ANNPendingLayers · ANNParking
       ▼
  presenters: ANNComponentPresenter · ANNTopBarPresenter · ANNStackPresenter · ANNBottomTabsPresenter ·
              ANNSideMenuPresenter · ANNSplitViewPresenter · ANNTransitionPlan/ANNTransitionAnimator · ANNSystemUI
       ▼
  ANNAppearanceDispatcher → ANNNavigationState → ANNCommandAnswer ─ ANNEventEmitter → ANNTurboEventEmitter ─► ANN.* events
```

One line per layer. Each layer calls the next one down, and the answer and the events come back up through the
dispatcher.

- **Way in** (`ANNTurboModule`): the codegen methods. An async method hands its work to `ANNCommandRunner`; a sync
  getter reads a store and never touches the main queue.
- **Layout check** (`ANNLayoutReader`, `ANNLayoutNode`): reads the JSON into immutable nodes and checks the whole
  tree before any view exists. The first error is the rejection text.
- **Command runner** (`ANNCommandRunner`, `ANNCommandAnswer`): takes the generation of the JavaScript, runs the check on
  the module queue, then runs the body on the main queue inside an exception guard. The answer resolves or rejects once.
- **Registry and factory** (`ANNNodeRegistry`, `ANNLayoutFactory`): the factory turns a checked tree into view
  controllers and registers every node by id.
- **Nodes** (`ANNLayoutProtocol`, `UIViewController+LayoutProtocol`, `ANNNodeHelper`): the view controllers. Each holds
  its layout data and its own options; the helper does the common work.
- **Options** (`Options/`): a typed tree per node. Parsed once, merged by one rule, resolved as own + parents +
  defaults, cached until `ANNOptionsCache` moves.
- **Coordinators**: the rules of one area. They own queues, waits and records, never views of React Native.
- **Presenters**: apply the resolved options to UIKit (bars, tabs, sheets, transitions, the system UI). They read
  options and write UIKit state; they send no appearance event.
- **Appearance dispatcher** (`ANNAppearanceDispatcher`): the only sender of the appearance events; it also ends a
  transition in the fixed order and resolves the command's answer.
- **Event emitter** (`ANNEventEmitter`, `ANNTurboEventEmitter`): sends `ANN.<Name>` to JS on the main queue, and drops
  events of a JavaScript that went. The names and keys come from `ANNEventContract`, made from the event types of
  `src/interfaces/` (`scripts/contract.mjs`).

## How a command flows

`navigation.push('Profile')` from Home: JS builds a layout node `{ id, type: 'Component', data, children }`, and calls
`push(commandId, componentId, layout)`.

1. **`ANNTurboModule push:componentId:layout:resolve:reject:`** (module queue). Builds the failure text
   (`could not push the screen '…'`) and calls `+[ANNCommandRunner run:…]` with a check block and a body block.
2. **`ANNCommandRunner`** (module queue). Takes `ANNReloadCoordinator.generation`. Runs the check:
   `+[ANNLayoutReader nodeFrom:error:]` reads and validates the whole tree into `ANNLayoutNode`s. A failure rejects here,
   through `ANNErrorHandler`, and nothing is queued. Otherwise it makes the `ANNCommandAnswer` and hands one task to the
   main queue.
3. **Main queue.** The task does nothing when the JavaScript went (`answer.isCurrent`), rejects
   `+[ANNErrorTexts nowhereToShow]` when there is no window, then runs the body in `@try`. The body finds Home in
   `ANNNodeRegistry` and calls `+[ANNStackCommands push:componentId:what:answer:]`.
4. **`ANNStackCommands`.** Finds the stack by walking `layoutParent`; checks `ANNLayoutValidator` (an id already shown,
   what a stack takes); reads the options of the whole tree with `ANNLayoutTreeOptions` (images load here); checks
   `ANNLayoutFactory canMakeTrees:`; a node with `preview.nativeID` goes to `ANNPreviewPush`; a `key` of an entry below
   goes back to that entry; else `+[ANNLayoutFactory nodeFromLayout:options:]` makes Profile (an
   `ANNComponentViewController` with an `ANNComponentPresenter`, content from `ANNReactViewCreator`) and registers it.
   No view is rendered yet.
5. **`-[ANNStackQueue push:answer:…]`.** The node joins `entries` now (every check reads `entries`), the operation is
   queued, `runNext` starts it when the queue is at rest.
6. **`startPush:` and `startPushTransition:`.** By `animations.push.waitForRender` the queue waits through
   `ANNRenderWait`, for the content and the bar components or for the bar components alone (a screen that waits for its
   content is parked unseen in the stack's view by `ANNParking`, `stackPark:`), then renders the node, drives the appearance of Home and Profile (`+[ANNAppearanceDispatcher driveAppearanceOf:]`), and calls
   `+[ANNAppearanceDispatcher beginTransitionShowing:]`. Profile's `ComponentWillAppear` is sent now (held until its
   content mounted, 0.5 s at most). `stackChange:` pushes in UIKit; with an own animation `ANNTransitionPlan` and
   `ANNStackAnimatingDelegate` give UIKit the `ANNTransitionAnimator`.
7. **The end.** `stackWhenTransitionEnds:` fires from the transition coordinator and calls `endPush:`, which calls
   `+[ANNAppearanceDispatcher endTransitionHiding:showing:events:answer:value:]`. In this order: `ComponentDidDisappear`
   of Home, `ComponentDidAppear` of Profile, `ANN.NavigationStateChanged` (`+[ANNNavigationState updateNow]`), the
   transition's `events`, then `-[ANNCommandAnswer resolve:]`: the state again if it changed
   (`reportBeforeAnswerOf:`), the promise with Profile's id, then `ANN.CommandCompleted`. `finish:` frees the queue for
   the next operation.

Events go out through `ANNEventEmitter` on the main queue and reach JS through `ANNTurboEventEmitter`.

How the others differ:

- **`setRoot`** (`ANNRootCoordinator`). At most one is pending; a newer one supersedes it (last wins). The root is made,
  rendered at the window's size and parked under the current root, waits for its render (`ANNRenderWait`, and the
  presentation gate when a presentation runs), then replaces the window's root by a dissolve or by its own animation
  (`ANNTransitionAnimator`). The modals and overlays of the layout show after the root, one at a time, with the
  navigation state held (`+[ANNNavigationState holdUpdates]`) until the answer. The old root's modals go with it
  without `ANN.ModalDismissed`.
- **`showModal`** (`ANNModalCoordinator`). One `ANNModal` record per modal. The tree is made and rendered, the record
  waits parked in the window, then its present is a step in `ANNPresentationGate`. `ANNModalPresentation` decides the
  style from the options; sheets and popovers go through `ANNSheetPresenter` and `ANNPopoverPresenter`; a custom show
  goes through `ANNModalTransitions`. The answer comes with the root's id once the modal is presented, after the state.
- **`mergeOptions`** (no promise, `-[ANNTurboModule mergeOptions:options:]`). Runs through `runVoid:` in one Core
  Animation transaction. The node merges the options into its own (`mergeOwnOptions:`, which bumps the options cache),
  its presenter applies what the merge names, the merge goes up the layout parents (`mergeChildOptions:child:`), then the
  modal and overlay of the node take it and `ANNSystemUI` asks UIKit again for the groups it names.

## The single places

Each of these owns one rule. Call it; do not copy it.

- `ANNCommandRunner`: The one path of every async command: generation, check, main queue, exception guard. Called by: `ANNTurboModule`.
- `ANNLayoutReader`: The layout check, depth first, the same order and texts as Android. Module queue. Called by: The runner's check blocks.
- `ANNCommandAnswer`: The answer: state, then promise, then `ANN.CommandCompleted`; once; silent for a JavaScript that went. Called by: Every coordinator and queue.
- `ANNNodeRegistry`: Every node by id, weakly. Filled by the factory, cleared at a reload. Called by: Commands, modules, `findViewController:`.
- `ANNLayoutFactory`: The core code that names module classes (`ANNSplitViewController`, `ANNSideMenuViewController`, `ANNArrangementViewController`); `ANNSideMenuPresenter` is the one other file that does. One maker per layout type; registers each node. Called by: Every command that shows a layout.
- `ANNOptionsCache`: The one invalidation point of resolved options. Called by: The setters of `ANNNodeHelper.options` and of its user layer (`setUserChoice:forKey:`), and `ANNDefaultOptions`, only.
- `ANNNodeHelper resolveOptionsWithDefault`: What a node shows with: defaults, parents' own options (each with its user layer), the node's `resolveOptions` (own, its user layer, then the current child down). Cached, read-only. Called by: Presenters, coordinators.
- `ANNAppearanceDispatcher`: The only sender of `ComponentWillAppear`, `ComponentDidAppear`, `ComponentDidDisappear`; the end-of-transition order; the events of a node that goes. Called by: Screens, queues, coordinators.
- `ANNNavigationState`: The visible screen and the nearest tabs it is in (the last tabs met walking down); coalesced updates, holds, the state before an answer. Called by: The dispatcher, `ANNCommandAnswer`, `ANNRootCoordinator`.
- `ANNScreenReadiness`: The signals of a screen's safe area view (has one, laid out, content rendered), sticky, by screen id. Called by: `ANNSafeAreaViewComponentView`, `ANNReactView`.
- `ANNRenderWait`: The one wait for the render of what a command shows: content, bar components, limits, the unknown-content rule. Called by: Root, stack queue, modals, overlays, `showInColumn`.
- `ANNParking`: Where a view waits for its content unseen: the window, a stack's view, a preview's host view. Called by: Root, stack, modals, overlays, `ANNPreviewPush`.
- `ANNPendingLayers`: Layers that wait to show (a pending root, a modal, an overlay); a node inside counts as off screen. Called by: The layer's owner; nodes ask.
- `ANNShownScreens`: "What a node shows changed": tells the pending layer, the modal hold and the system UI, in that order. Called by: Every container that changes what it shows.
- `ANNModalCoordinator`, `ANNModal`: One record per modal, its states and waiting commands. Called by: Commands, root, state, repaint, reload.
- `ANNPresentationGate`: The one queue of every UIKit present and dismiss in the main window. Called by: Modals, root, reload, search.
- `ANNOverlayCoordinator`: Overlays, each in its own window, kept over a new root. Called by: Commands, root, reload.
- `ANNSystemUI`: The system UI of the windows: marks groups, coalesces, asks UIKit once per turn; who decides; after a status bar ask, the screens' status bar blurs take its frame. `ANNSystemUIRules` has the pure rules. Called by: Screens, containers, `ANNShownScreens`, `mergeOptions`.
- `ANNReloadCoordinator`: The generation of the JavaScript; the reset after a reload. Called by: Runner, emitter, answer.
- Stack answer id: `ANNStackAnswerIdOf(entry, command)` in `ANNStackQueue.h`: the id a stack command answers with. `push` answers the node's own id (a pair's id); every other command, the entry's options screen (a pair's primary). Called by: The queue, `ANNStackCommands`, `ANNPreviewPush`, `ANNSplitViewCommands`.
- Waited screens: `waitedComponentViewControllers`: the screens a wait is about (a pair: the ones it puts in the window). `ANNStackScreensOfEntry` is every screen of an entry, for what means all of them. Called by: `ANNRenderWait` owners: root, stack queue, modals, overlays, `ANNSplitViewCommands`.
- Back: The stack: `ANNStackController` (`userGoBackTo:`, `backAction`, the hold) and `InteractivePopGestureDelegate`; UIKit's own pops are followed by `ANNStackQueue uikitShows:`. Called by: UIKit, the user.

## Modules

Modules are the feature folders that plug into the core through the interface in
`ARCHITECTURE.md` section 4: the node protocol and its helper, the presenter hooks, the capabilities and
the stores. By area:

- **Containers:** `ANNSideMenuViewController` and `ANNSideMenuChildViewController`;
  `ANNSplitViewController` with `UISplitViewController+ANNOptions`;
  `ANNArrangementViewController`; `UITabBar+utils`, `ANNBottomTabsAccessoryView`.
- **Modals:** `ANNSheetPresenter`, `ANNPopoverPresenter`, `ANNModalSizePresenter`, `ANNModalHostViewComponentView`;
  `ANNOverlayWindow`.
- **Screen and bar:** `ANNPreviewInteraction`, `ANNScreenInsets`, `ANNScreenRegions`, `ANNNativeScreenContext`,
  `ANNNativeScreenCalls`, `ANNSearchPresenter`, `ANNSearchResultsViewController`; options `ANNMenuOptions`,
  `ANNToolbarOptions`, `ANNSheetOptions`, `ANNPopoverOptions`, `ANNBottomTabsAccessoryOptions`.
- **React and C++:** `React/Slots/`, `ANNSafeAreaViewComponentView`, the Fabric files of `cpp/`.
- **App start:** `Public/ApexNavigation/ApexNavigation.h`, `Core/ANNSceneDelegate.mm`, `ANNKeyCommands`,
  `ANNDeepLinkQueue`, `ANNHinge`.

What a module uses of the core:

- **The node protocol** `ANNLayoutProtocol`: required methods (`getCurrentChild`, `layoutChildren`,
  `shownComponentViewControllers`, `render`, `readyForPresentation`, `resolveOptions`, `mergeOptions:`,
  `mergeChildOptions:child:`, `destroy`, `systemUIChild`, …) and optional ones. `UIViewController+LayoutProtocol` gives
  every view controller safe defaults and its `nodeHelper` (`ANNNodeHelper`): data, own options, resolved options,
  ready handlers, registry entry. A container answers the methods that differ and does the common part with its
  helper, never through `super`. Containers cannot share a base class (they inherit UIKit's), so the protocol and the
  helper are the shared part.
- **Capabilities** are optional methods of the protocol; a node without one answers "no": `waitedComponentViewControllers`,
  `waitedReadinessOfScreen:`, `drivenDescendantDidAppear:`, `descendantDidAppear:`, `childDidDisappear:`, `goBackFromColumnOf:completion:`,
  `screensLeavingWithNode`, `childOffsetFromRest:`, `optionsScreen`, `keepsStatusBarRoom`, `viewControllerOfColumn:`,
  `isCollapsedIntoCompact`, `showScreen:inColumn:…`. The core asks with `respondsToSelector:` rather than by class; a few class checks remain (a screen reaches its
  stack and its tabs by class).
- **The presenter hooks** of `ANNNodePresenter`: `applyOptions:`, `mergeOptions:resolvedOptions:`,
  `applyDefaultOptionsChange:`, `descendantWillAppear:`, `mergeChildOptions:`, `optionsForParent:`, and the answers to
  UIKit (status bar, orientations, home indicator, deferred edges).
- **The stores** a module writes on the main queue and JS reads from any thread: `ANNScreenInsets`,
  `ANNScreenRegions`, `ANNArrangementState`; and the signals `ANNScreenReadiness`, `ANNSlotContainers`.
- **A module's options group** subclasses `ANNOptions` and overrides `initWithDict:`, `mergeOptions:` and `hasValue`.

Core code that lives in a module's folder: `ANNSplitViewPresenter` (turns `splitView.*` into calls of
the module's category) and `ANNSplitViewCommands` (`showInColumn`, `setColumnShown`). They are the
core's; only `ANNLayoutFactory` and `ANNSideMenuPresenter` name a module class.
