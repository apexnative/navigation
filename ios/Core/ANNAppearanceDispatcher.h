#import "ANNScreenAppearance.h"
#import <UIKit/UIKit.h>

@class ANNCommandAnswer;

NS_ASSUME_NONNULL_BEGIN

/// The one place that sends `ANN.ComponentWillAppear`, `ANN.ComponentDidAppear` and `ANN.ComponentDidDisappear`, and
/// the events of a transition and of a node that goes. Everything is synchronous, on the main queue: the only delay is
/// the hold of an appearance until its content mounted, at most 0.5 s, and it never delays a transition.
///
/// Per record (`ANNScreenAppearance`): at most one `WillAppear` and one `DidAppear` per appearance, `DidAppear` only
/// after `WillAppear`, `DidDisappear` only after a `WillAppear` JavaScript heard. A record of a JavaScript that went
/// sends nothing: its emitter is silent.
@interface ANNAppearanceDispatcher : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

#pragma mark - A record

/// The appearance starts: `WillAppear`, now when the record is ready, else held. Nothing when it started already.
+ (void)willAppear:(ANNScreenAppearance *)appearance;

/// The appearance ended: `WillAppear` first when it wasn't asked, then `DidAppear`, now when nothing is held and the
/// record is ready, else held behind what is held. Nothing when it was asked already.
+ (void)didAppear:(ANNScreenAppearance *)appearance;

/// The thing went or got covered: `DidDisappear` when JavaScript heard the `WillAppear`; when the `WillAppear` is still
/// held, what is held is dropped and nothing goes (JavaScript never heard of the appearance). Nothing while hidden.
+ (void)didDisappear:(ANNScreenAppearance *)appearance;

/// The record is ready (`markReady`, the mount of its content, the hold limit): what is held goes now, in order.
+ (void)appearanceBecameReady:(ANNScreenAppearance *)appearance;

/// `block` runs right after the `DidAppear` of `appearance` is sent, in the same turn (also a held one, when it goes):
/// at once when it was sent already or no appearance is pending (the record is hidden, or there is none), and when the
/// appearance ends without its `DidAppear` (the content went before). Never dropped: a tab switch sends
/// `ANN.BottomTabSelected` this way.
+ (void)afterDidAppearOf:(nullable ANNScreenAppearance *)appearance run:(dispatch_block_t)block;

#pragma mark - Nodes

/// Each screen `node` shows (`shownComponentViewControllers`) starts to appear.
+ (void)nodeWillAppear:(UIViewController *)node;

/// Each screen `node` shows appeared (with `WillAppear` first where it wasn't asked).
+ (void)nodeDidAppear:(UIViewController *)node;

/// Every screen of the node's subtree (`layoutChildren`, depth first) disappeared; those not shown ignore it.
+ (void)nodeDidDisappear:(UIViewController *)node;

#pragma mark - Transitions

/// A transition starts: `nodeWillAppear:` of each node that comes. The screens it began are remembered (weakly) until
/// an end names a node that holds them.
+ (void)beginTransitionShowing:(NSArray<UIViewController *> *)shown;

/// A transition ended, in this order: first a screen a transition began inside a node of `shown` that left the window
/// and isn't one of that node's shown screens gets its `DidDisappear`; then `DidDisappear` of every screen of `hidden`,
/// `DidAppear` of what `shown` shows, the navigation state now when it changed, `events` (the transition's own events,
/// `ANN.ScreenPopped` and `ANN.ModalDismissed`, and what must follow them, like stopping the content that went), then
/// `answer` resolves with `value` (its own state step finds nothing new, then the promise, then
/// `ANN.CommandCompleted`). A user's action passes no answer. The callbacks of UIKit that come later find the phases
/// set and send nothing.
+ (void)endTransitionHiding:(NSArray<UIViewController *> *)hidden
                    showing:(NSArray<UIViewController *> *)shown
                     events:(nullable dispatch_block_t)events
                     answer:(nullable ANNCommandAnswer *)answer
                      value:(nullable id)value;

/// A switch of a container's shown child ended (a tab switch): the same close of the screens begun and gone first, then
/// `DidDisappear` of every screen of `hidden`, `DidAppear` of what `shown` shows, then `events`. Unlike
/// `endTransitionHiding:…` it reports no state now: the events that went ask for the coalesced one, which comes after
/// what `events` sends.
+ (void)endSwitchHiding:(NSArray<UIViewController *> *)hidden
                showing:(NSArray<UIViewController *> *)shown
                 events:(nullable dispatch_block_t)events;

#pragma mark - A command drives the appearance

/// From now on a command reports the appearance of `nodes` and of their layout subtrees itself (`setRoot` replacing
/// the window's root): UIKit's appearance callbacks of their screens send nothing, because UIKit calls them while the
/// root is replaced, out of the command's order. The dispatcher's own calls are not affected. Balanced by
/// `stopDrivingAppearanceOf:`; two commands may drive one node. Main queue.
+ (void)driveAppearanceOf:(NSArray<UIViewController *> *)nodes;

/// The command that drove `nodes` ended: UIKit's callbacks report them again once no command drives them.
+ (void)stopDrivingAppearanceOf:(NSArray<UIViewController *> *)nodes;

/// Whether a command drives the appearance of `screen`: it, or one of its layout parents, is driven.
+ (BOOL)isAppearanceDriven:(UIViewController *)screen;

#pragma mark - The events of a transition and of a node that goes

/// The node left its stack (EVT-ScreenPopped): `ANN.ScreenPopped` for each screen of its `screensLeavingWithNode` (a
/// pair: both), else for the node alone, then the node's own `screenPopped` cleanup. Call it inside `events` of the
/// transition, before the content of the node stops.
+ (void)screenPopped:(UIViewController *)node;

/// The visible screen of `node`: the last screen down its current children that are nodes of the layout; nil when that
/// chain has no screen. The one walk that names the screen of `ANN.ModalDismissed`, `ANN.ModalAttemptedToDismiss` and
/// `ANN.SheetDetentChanged`.
+ (nullable UIViewController *)visibleScreenOf:(UIViewController *)node;

/// The modal went (EVT-ModalDismissed): `ANN.ModalDismissed` with the id and the name of its visible screen
/// (`visibleScreenOf:`) and the number of modals that went with it. A chain with no screen sends the modal's own id
/// with an empty name.
+ (void)modalDismissed:(UIViewController *)modal count:(NSUInteger)count;

/// The native screen left the layout (EVT-NativeScreenReleased): `ANN.NativeScreenReleased`, once per node however it
/// went; nothing for a node of a JavaScript that went.
+ (void)nativeScreenReleased:(UIViewController *)screen;

@end

NS_ASSUME_NONNULL_END
