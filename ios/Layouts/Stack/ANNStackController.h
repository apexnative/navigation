#import "ANNLayoutNode.h"
#import "ANNLayoutProtocol.h"
#import "ANNStackPresenter.h"
#import "ANNStackQueue.h"
#import "UIViewController+LayoutProtocol.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The id of `ANN.NavigationButtonPressed` for a held back without its own `topBar.backButton.id`.
FOUNDATION_EXPORT NSString *const ANNBackButtonDefaultId;

/// The key a layout node brings into a stack: its `data.key`; a pair's is its primary's.
FOUNDATION_EXPORT NSString *_Nullable ANNStackKeyOfLayout(ANNLayoutNode *node);

/// A stack of the layout (`Stack`): UIKit's navigation controller with the screens one over another, large titles
/// allowed. It is its own delegate, which offers UIKit no animation (the content swipe works); while a custom animation
/// may be needed (the top's own `animations.pop`, a change with its own plan) an animating delegate takes its place and
/// gives UIKit the queue's custom transition (`ANNStackAnimatingDelegate`). Every command of the stack and every back
/// of the user goes through its one queue (`ANNStackQueue`); the core's own calls to UIKit come only from there.
/// UIKit's own `popToViewController:animated:` and `popToRootViewControllerAnimated:` (the history menu of the back
/// button, the tab bar's second tap) are the user's back when every view controller they remove is an entry of this
/// stack; its `popViewControllerAnimated:` (the swipe) and anything else go to UIKit, and the stack follows what UIKit
/// did.
///
/// Back: the system back button with `navigationItem.backAction` on every entry but the first (iOS 16+),
/// `navigationBar:shouldPopItem:` on iOS 15; the hold `topBar.backButton.popStackOnPress: false` of the screen on top
/// sends `ANN.NavigationButtonPressed` instead; the swipes back go through `InteractivePopGestureDelegate`. The core
/// never looks for UIKit's back button nor writes a `testID` to it.
@interface ANNStackController
    : UINavigationController <ANNLayoutProtocol, UINavigationControllerDelegate>

/// The stack with the screens it is made with, the last one on top: no transition, no event (it isn't shown yet).
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(nullable NSArray<UIViewController *> *)childViewControllers;

/// The stack's queue: the commands put their operations there (`ANNStackCommands`).
@property(nonatomic, strong, readonly) ANNStackQueue *queue;

/// The entry of the stack that `node` is, or holds it (a screen of a pair); in `queue.entries` or not.
- (nullable UIViewController *)entryOf:(UIViewController *)node;

/// The key of an entry, kept as it joins the stack.
- (void)setKey:(nullable NSString *)key ofEntry:(UIViewController *)entry;

/// The entry of `queue.entries` with `key`; nil when none has it.
- (nullable UIViewController *)entryWithKey:(NSString *)key;

#pragma mark - The user's back

/// The back button, the history menu: the hold of the screen on top, else the stack goes back to `target` (one entry
/// back when nil) with the leaving top's `animations.pop`. Nothing with one entry (C2).
- (void)userGoBackTo:(nullable UIViewController *)target;

/// The second tap on the selected tab (a cell of the tabs' custom row; UIKit's own tap comes through
/// `popToRootViewControllerAnimated:`): back to the first entry, the hold first.
- (void)userPopToRoot;

/// `popToRootOnBlur`: back to the first entry without an animation and without appearance events; each entry that
/// leaves gets `ScreenPopped`, top to bottom, and is destroyed.
- (void)popToRootSilently;

#pragma mark - More

/// The navigation controller the stack's screens show in: UIKit's More navigation while a tab of More holds them (its
/// own list is empty then), else the stack itself. The bar, the toolbar, "on screen" and the transitions are its.
@property(nonatomic, readonly) UINavigationController *shownNavigationController;

/// UIKit's More navigation is about to show one of the stack's screens (its delegate, the tabs, tells): the shared
/// parts of the bar go onto it, and at the end of its transition the stack follows a back the user made there — only
/// while the stack's first screen stays on it; UIKit taking the screens back (the More tap, another tab, a size change)
/// changes nothing.
- (void)moreNavigationWillShow:(UINavigationController *)moreNavigation;

/// UIKit's More navigation shows its list again: the shared parts of the bar are set anew the next time the stack's
/// screens show there (the list keeps UIKit's own bar).
- (void)forgetSharedBar;

#pragma mark - Tabs

/// The stack left the screen while its tabs drove the appearance of a switch (UIKit's `viewDidDisappear:` reported
/// nothing then): what waits for its screen runs now, as at its own disappearance.
- (void)stackDidLeaveScreen;

#pragma mark - The swipes back (asked by `InteractivePopGestureDelegate`)

/// The swipe of `recognizer` is UIKit's: UIKit's top isn't the stack's own (a column UIKit's split view moved into
/// it), or UIKit's More navigation shows the stack's first screen only (its swipe goes back to the More list).
- (BOOL)swipeBackIsUIKits:(UIGestureRecognizer *)recognizer;

/// The swipe of `recognizer` may play the pop: the stack's own swipe (from the leading edge, toward the trailing side)
/// only while the top's pop follows the finger (`animations.pop.interactive`) and the screen under is in place, UIKit's
/// swipes of the stack's navigation only while it doesn't. Every other swipe may.
- (BOOL)swipeBackTakes:(UIGestureRecognizer *)recognizer;

/// `recognizer` is the stack's own swipe back, not one of UIKit's.
- (BOOL)swipeBackIsStacks:(UIGestureRecognizer *)recognizer;

/// The stack has one entry: there is nowhere to go back to.
@property(nonatomic, readonly) BOOL hasOneEntry;

/// A swipe may begin: more than one entry, and the queue at rest.
- (BOOL)swipeBackMayBegin;

/// The screen on top holds the back: its event goes (once per touch, for both swipes), and the swipe doesn't begin.
- (BOOL)holdsSwipeBack;

/// The screen on top holds the back (`popStackOnPress: false` with the stack's options and the defaults); nothing sent.
@property(nonatomic, readonly) BOOL backIsHeld;

/// A swipe of the stack takes `touch`: the hold's event is sent once for it.
- (void)swipeBackReceivesTouch:(UITouch *)touch;

/// `popGesture` of the screen on top into the swipes' delegates (`popGesture`).
- (void)updateSwipeBack;

/// The bar of the navigation that shows the stack's screens may have shown or hidden (by the options, a scroll, a tap,
/// the keyboard): the swipes' delegates follow it.
- (void)swipeBackFollowsBar;

#pragma mark - The bar

/// The parts of the bar the stack shares follow the screen UIKit shows on top (its options screen): hiding modes,
/// style, `testID`, the screen's React background, and the visibilities `visibility` names (`ANNStackPresenter`).
/// `animated`: along the stack's own transition when one runs (its coordinator), else animated in a window by
/// `topBar.animate`.
- (void)applySharedBarAnimated:(BOOL)animated visibility:(ANNSharedBarVisibility)visibility;

/// The same, with both visibilities only when the screen on top isn't the one they were last set for (a push, a pop,
/// a cancelled swipe), so that a bar the user hid stays hidden while the same screen stays on top.
- (void)applySharedBarIfTopChangedAnimated:(BOOL)animated;

/// The shown screens apply their "under bars" rule again: the stack changed a bar (`hasShownTopBar`).
- (void)updateRootLayoutOfShownEntries;

/// The back action of an entry (iOS 16+, the hold): on every entry but the first whose item has no left buttons.
/// UIKit makes a back button for an item with a back action, so an item whose left buttons replace the back button has
/// none.
- (void)updateBackActionOf:(UIViewController *)entry;

#pragma mark - Direction

/// The stack follows the scene's direction again: its view and bar lose the direction a push forced (iOS 17+, when
/// the app's direction changes while the stack shows).
- (void)dropForcedDirection;

#pragma mark - The background (`layout.backgroundColor`)

/// The stack's view takes its resolved `layout.backgroundColor` (the top's over the stack's over the defaults).
- (void)applyBackground;

#pragma mark - The split view

/// `children` in place of the stack's entries, without a wait: `completion` at the end, `rejection` with the texts of
/// the stack's commands, naming `fromViewController`.
- (void)setChildren:(NSArray<UIViewController *> *)children
    fromViewController:(nullable UIViewController *)fromViewController
              animated:(BOOL)animated
            completion:(ANNTransitionCompletionBlock)completion
             rejection:(ANNTransitionRejectionBlock)rejection;

@end

NS_ASSUME_NONNULL_END
