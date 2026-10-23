#import "ANNArrangementScreenDelegate.h"
#import "ANNLayoutProtocol.h"
#import "ANNScreenAppearance.h"
#import "ANNScreenPlace.h"
#import "UIViewController+LayoutProtocol.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A screen of the layout (`Component`): its content is a React view, or the slot screen view of one React tree.
///
/// Its view has the size of the screen of the navigation window's scene; the content sits in the root frame, by the one
/// "under bars" rule (`ANNScreenBars`). Every recompute of the frame (a layout, a change of the safe area or the
/// traits, `mergeOptions`, an appearance) gives the modules the screen's insets, padding and regions
/// (`ANNScreenInsets`, `ANNScreenRegions`). Its presenter is an `ANNComponentPresenter`.
///
/// The screen answers UIKit's questions about the system UI (native screens too): the status bar's style and visibility
/// (never animated), the orientations, the home indicator, the deferred edges and the vertical bars, from its resolved
/// options by its presenter's rules; a screen of an overlay by the overlay's own options, or as the main window's
/// deciding controller answers for what the overlay doesn't set (`ANNSystemUI`). Its appearance and disappearance mark
/// the system UI's owner.
///
/// The screen reports its appearance through its content's record (`ANNScreenAppearance`, sent by
/// `ANNAppearanceDispatcher`): UIKit's appearance callbacks, which reach a child through containment, and the
/// dispatcher's node walkers both call `screenWillAppear`, `screenDidAppear`, `screenDidDisappear`; whichever comes
/// second finds the phase set and sends nothing. UIKit's callbacks do nothing while a command drives the screen's
/// appearance (`+[ANNAppearanceDispatcher isAppearanceDriven:]`).
@interface ANNComponentViewController : UIViewController <ANNLayoutProtocol>

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(nullable NSArray<UIViewController *> *)childViewControllers;

/// What the screen shows; nil before the screen made it (`render`).
@property(nonatomic, strong, readonly, nullable) UIView<ANNComponentProtocol> *reactView;

/// Makes the content with the node's creator, once, in the root frame of the screen's view: a React surface starts,
/// or the slot screen view takes its slot. The screen is ready (`readyForPresentation`) once its content is. A screen
/// nobody rendered gets its content when UIKit first loads its view.
- (void)render;

/// Where the screen will show while it waits for its content out of it (parked by a stack's or `showInColumn`'s wait,
/// `ANNParking`): it lays out with that place's safe area and bars (`ANNScreenPlace`), as it will once it shows there.
/// Asked at each layout, and kept past the parking until UIKit gives the screen a safe area of its own in a parent
/// (the push lays it out once before that, with none), or as it appears. Nil: its view's own. Set by the one that
/// parks the screen.
@property(nonatomic, copy, nullable) ANNScreenPlace *_Nullable (^placeWhileParked)(void);

/// Applies the "under bars" rule to the root frame again, then gives the modules the insets, the padding and the
/// regions of the screen. Nothing before the view loaded; the safe area only in a window.
- (void)updateRootLayout;

/// The content view (only the core's React view, never the screen's own view) mounts unseen in `host` (the tabs'
/// preload window) at the frame it has, while the screen's tab isn't shown. NO when there is nothing to lend: no React
/// view of its own, in a window already, lent already, or the screen went.
- (BOOL)lendContentTo:(UIView *)host;

/// The lent content comes back into the screen's view at its root frame; also as the screen first appears.
- (void)takeContentBack;

/// The content is lent (`lendContentTo:`): `updateRootLayout` leaves its frame alone meanwhile.
@property(nonatomic, readonly) BOOL contentIsLent;

/// The transition that removes the screen starts (before its destroy): with `blurOnUnmount` in its resolved options,
/// the editing inside its view ends and the keyboard goes (`blurOnUnmount`).
- (void)screenWillLeave;

/// `screenWillLeave` of every screen of the layout subtree of `node`.
+ (void)nodeWillLeave:(UIViewController *)node;

/// The navigation controller that shows the screen's stack: the stack, or UIKit's More navigation while a tab of More
/// holds its screens; nil out of a stack. Its bar is the one `statusBar.hideWithTopBar` follows.
- (nullable UINavigationController *)shownNavigation;

/// The pair the screen is in (`ANNArrangementViewController`): the screen tells it when it lays out, appears,
/// disappears or its traits change, and when it sets its toolbar items.
@property(nonatomic, weak, nullable) id<ANNArrangementScreenDelegate> arrangement;

/// `keyCommands`: the screen becomes the first responder when it is in a window, has commands (its own, else its
/// nearest layout parent's) and nothing takes the keyboard; never while it waits unseen (parked, or in a layer that
/// waits to show) or is a preview's screen. The screen tries after its appearance, once JavaScript heard its
/// `DidAppear`, and on the next turn after an edit ended; a merge that names `keyCommands` makes the screens of its
/// node try. Whether it became the first responder.
- (BOOL)takeKeyCommandsFocus;

/// The record the screen reports its appearance with: its content's, or a native screen's own; nil while a React
/// screen has no content yet. A command whose readiness wait ended calls its `markReady`.
@property(nonatomic, readonly, nullable) ANNScreenAppearance *appearance;

/// The screen starts to appear, appeared, disappeared: `ANN.ComponentWillAppear`, `DidAppear`, `DidDisappear` through
/// the dispatcher. `screenWillAppear` first applies the screen's resolved options (its presenter's `applyOptions:`).
- (void)screenWillAppear;
- (void)screenDidAppear;
- (void)screenDidDisappear;

/// Once, in this order: `DidDisappear` if the screen showed (where UIKit doesn't send it: `setRoot`, `dismissOverlay`,
/// a pop that stops the content), the registry forgets it, its content stops, the content view leaves the screen's
/// view, and its data by id (insets, regions, the pair's state, readiness) goes when no other node has the id. A second
/// call does nothing.
- (void)destroy;

@end

NS_ASSUME_NONNULL_END
