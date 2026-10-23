#import "ANNCommandAnswer.h"
#import "ANNLayoutProtocol.h"
#import <UIKit/UIKit.h>

@class ANNComponentViewController;
@class ANNTransitionPlan;

NS_ASSUME_NONNULL_BEGIN

/// The screen whose options an entry of a stack shows with: a pair's `optionsScreen` (its primary), else the entry.
FOUNDATION_EXPORT UIViewController *ANNStackOptionsScreen(UIViewController *entry);

/// The stack command that answers with the id of an entry (`ANNStackAnswerIdOf`).
typedef NS_ENUM(NSInteger, ANNStackAnswerCommand) {
    /// `push`, also one interrupted before it showed.
    ANNStackAnswerCommandPush,
    /// `setStackRoot` and every other replace of the list: its new top.
    ANNStackAnswerCommandReplace,
    /// `pop` from the stack's id: the top that leaves.
    ANNStackAnswerCommandPop,
    /// `popToRoot`: the first entry.
    ANNStackAnswerCommandPopToRoot,
    /// A push or a preview whose key names an entry of the stack: that entry.
    ANNStackAnswerCommandKey,
    /// `showInColumn` that put the screen in a column: the screen.
    ANNStackAnswerCommandShowInColumn,
};

/// The one rule of the id a stack command answers with for an entry: `push` answers the node's own id (a pair's id for
/// a pair), every other command the id of the entry's options screen (`ANNStackOptionsScreen`: a pair's primary). An
/// entry that isn't a pair answers its own id with every command. A command that answers the id it was given (`popTo`,
/// `pop` from a screen) doesn't ask.
FOUNDATION_EXPORT NSString *ANNStackAnswerIdOf(UIViewController *entry,
                                               ANNStackAnswerCommand command);

/// Every screen of an entry of a stack: the entry itself, or the screens of a pair, also one out of the window. Not the
/// screens a wait is about: those are the entry's `waitedComponentViewControllers` (a pair: the screens it puts in the
/// window).
FOUNDATION_EXPORT NSArray<ANNComponentViewController *> *
ANNStackScreensOfEntry(UIViewController *entry);

/// The readiness test of a waited entry after its `render`: each of its screens has no content to wait for (a native
/// screen) or its content is ready.
FOUNDATION_EXPORT BOOL ANNStackEntryIsReady(UIViewController *entry);

/// How UIKit's list of a stack changes.
typedef NS_ENUM(NSInteger, ANNStackChange) {
    /// The last entry comes on top (`pushViewController:animated:`).
    ANNStackChangePush,
    /// Back to the last entry (`popToViewController:animated:`).
    ANNStackChangePop,
    /// The whole list (`setViewControllers:animated:`).
    ANNStackChangeReplace,
};

/// What the queue asks of its stack (`ANNStackController`): the calls to UIKit, whether the stack is seen, the
/// waiting place of a screen. The queue itself knows no view.
@protocol ANNStackDisplay <NSObject>

/// The stack's own view controller: the layout parent of the nodes it took and doesn't show yet.
- (UIViewController *)stackViewController;

/// The stack is seen: its view is in a window, and it isn't inside a layer that waits to show (`ANNPendingLayers`).
- (BOOL)stackIsOnScreen;

/// A transition UIKit runs on the stack now (one the queue didn't start); nil when none runs.
- (nullable id<UIViewControllerTransitionCoordinator>)stackRunningTransition;

/// UIKit's list becomes `entries`, by `change`. `plan`: the change's own custom transition, which UIKit gets when it
/// asks for the transition of this change; nil: UIKit's own (or a zoom of the pushed screen).
- (void)stackChange:(ANNStackChange)change
          toEntries:(NSArray<UIViewController *> *)entries
           animated:(BOOL)animated
               plan:(nullable ANNTransitionPlan *)plan;

/// `ended` runs once, when the transition UIKit has just started ends (its coordinator, never `CATransaction`), or at
/// once when UIKit runs none. `cancelled` only for an interactive one.
- (void)stackWhenTransitionEnds:(void (^)(BOOL cancelled))ended;

/// A screen waits for its content in the window, at the stack's size, unseen; and leaves that place.
- (void)stackPark:(UIViewController *)entry;
- (void)stackUnpark:(UIViewController *)entry;

/// An entry joins UIKit's list (its back button, its root frame); `first` is the stack's first entry.
- (void)stackEntryJoins:(UIViewController *)entry first:(BOOL)first;

/// UIKit's list is about to lose entries or change whole: the lower screens that render in the stack's view leave it.
- (void)stackEntriesWillMove;

/// The lower new entries of a list made their way into UIKit's list: they make their content now.
- (void)stackRenderLowerEntries:(NSArray<UIViewController *> *)entries;

/// An operation ended: the top may be another one.
- (void)stackOperationEnded;

@end

/// The one queue of a stack: every command of the stack and every action of the user goes through it, in order. It
/// keeps the stack's two lists: `entries`, the stack as JavaScript sees it, changed when a command or an action is
/// accepted (every check reads it), and `shown`, what UIKit's list has, changed as each operation runs. It is at rest,
/// waits for a screen to render, or runs one UIKit transition; what comes meanwhile interrupts, completes or waits.
/// Each operation answers once, with the end steps, `ANN.ScreenPopped` of every entry that left before its content
/// stops. Main queue.
@interface ANNStackQueue : NSObject

- (instancetype)initWithDisplay:(id<ANNStackDisplay>)display NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The stack as JavaScript sees it, bottom to top.
@property(nonatomic, readonly) NSArray<UIViewController *> *entries;

/// What UIKit's list has of the stack's entries, bottom to top; its last is the stack's current child.
@property(nonatomic, readonly) NSArray<UIViewController *> *shown;

/// Nothing runs, nothing waits, nothing is queued, and UIKit runs no transition of its own.
@property(nonatomic, readonly) BOOL isAtRest;

/// Every node the stack holds: those shown, those waiting, those taken and not run yet, those leaving.
@property(nonatomic, readonly) NSArray<UIViewController *> *heldNodes;

/// The list the stack was made with (the layout factory): shown and entries both, no transition, no event.
- (void)setInitialEntries:(NSArray<UIViewController *> *)entries;

#pragma mark - Operations

/// `push` of `node`, made and checked: it joins `entries` now and shows by the queue's rules. `componentId` is the
/// command's (for its texts), `source` the node it came from.
- (void)push:(UIViewController *)node
         answer:(ANNCommandAnswer *)answer
           what:(NSString *)what
    componentId:(NSString *)componentId
         source:(nullable UIViewController *)source;

/// The same with two of the rules set by the caller: `animated` NO pushes without an animation whatever the node's
/// `animations.push` (a preview that grows into its screen), YES as they say; `waits` NO never waits for a render
/// before the transition (a screen rendered already: a preview, `showInColumn`'s push).
- (void)push:(UIViewController *)node
         answer:(ANNCommandAnswer *)answer
           what:(NSString *)what
    componentId:(NSString *)componentId
         source:(nullable UIViewController *)source
       animated:(BOOL)animated
          waits:(BOOL)waits;

/// Every entry above `target` leaves (`pop`, `popTo`, `popToRoot`, a key that leads back; a user's back with no
/// answer). The visible top leaves with its `animations.pop`, the others without an animation; `silent` moves without
/// an animation and without appearance events (`popToRootOnBlur`).
- (void)cutTo:(UIViewController *)target
         answer:(nullable ANNCommandAnswer *)answer
          value:(nullable id)value
           what:(NSString *)what
    componentId:(nullable NSString *)componentId
         source:(nullable UIViewController *)source
         silent:(BOOL)silent;

/// `nodes` in place of every entry (`setStackRoot`; the split view's call with `waits` NO). The answer or the
/// completion comes at the end; the rejection of a split view's call when it fails.
- (void)replaceWith:(NSArray<UIViewController *> *)nodes
           animated:(BOOL)animated
              waits:(BOOL)waits
             answer:(nullable ANNCommandAnswer *)answer
               what:(NSString *)what
        componentId:(NSString *)componentId
             source:(nullable UIViewController *)source
         completion:(nullable dispatch_block_t)completion
          rejection:(nullable ANNTransitionRejectionBlock)rejection;

/// A command that moves nothing (`popTo` of the top) answers `value` in its turn; a failure meanwhile rejects with the
/// command's own `what`.
- (void)noneWithValue:(id)value
               answer:(ANNCommandAnswer *)answer
                 what:(NSString *)what
          componentId:(NSString *)componentId
               source:(nullable UIViewController *)source;

#pragma mark - The swipe back and the stack

/// UIKit started the interactive pop of the stack's top (`willShowViewController:` with an interactive coordinator):
/// the swipe is the queue's operation until its coordinator completes. NO when the queue doesn't take it.
- (BOOL)swipeBeganFrom:(UIViewController *)top revealing:(UIViewController *)revealed;

/// UIKit changed its list by itself while the queue didn't run it (its own pop, a swipe the queue didn't take): the
/// entries UIKit no longer lists leave the stack's lists, get `ScreenPopped` and are destroyed.
- (void)uikitShows:(NSArray<UIViewController *> *)viewControllers;

/// The stack left the screen: an operation that waits for its screen runs now, as in a stack off screen.
- (void)stackWentOffScreen;

/// The stack goes: the waiting, running and queued commands reject `the screen '<id>' is not shown`, the actions of the
/// user end without a word, nothing waits any more. The nodes to destroy come back.
- (NSArray<UIViewController *> *)destroyQueue;

@end

NS_ASSUME_NONNULL_END
