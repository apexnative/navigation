#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The groups of the system UI a screen decides: what a trigger marks for UIKit to ask again.
typedef NS_OPTIONS(NSUInteger, ANNSystemUIGroup) {
    ANNSystemUIGroupNone = 0,
    /// `statusBar.*`: the style and the visibility.
    ANNSystemUIGroupStatusBar = 1 << 0,
    /// `layout.orientation`.
    ANNSystemUIGroupOrientation = 1 << 1,
    /// `layout.autoHideHomeIndicator`.
    ANNSystemUIGroupHomeIndicator = 1 << 2,
    /// `layout.deferSystemGestures`.
    ANNSystemUIGroupDeferredEdges = 1 << 3,
    /// `layout.verticalBars` (iOS 27.1).
    ANNSystemUIGroupVerticalBars = 1 << 4,
    ANNSystemUIGroupAll = (1 << 5) - 1,
};

/// One question UIKit asks a view controller about the system UI; the status bar has two.
typedef NS_ENUM(NSInteger, ANNSystemUIQuestion) {
    ANNSystemUIQuestionStatusBarStyle,
    ANNSystemUIQuestionStatusBarHidden,
    ANNSystemUIQuestionOrientations,
    ANNSystemUIQuestionHomeIndicator,
    ANNSystemUIQuestionDeferredEdges,
    ANNSystemUIQuestionVerticalBars,
};

/// The one owner of the system UI of the navigation's windows.
///
/// UIKit keeps the values: it asks the view controllers itself at its transitions, rotations and trait changes, and the
/// screens answer from their resolved options (`ANNComponentViewController`, through the rules of `ANNNodePresenter`).
/// The owner adds the asks UIKit doesn't make — a merge, a side menu at rest, an overlay, a root replaced, the bar of a
/// stack shown or hidden, the orientation after an appearance — and makes them once per turn of the main queue: a
/// trigger marks groups, the marks of a turn are coalesced, and at its end UIKit is asked again for each marked group
/// in the main window and every overlay window. It keeps no values but the last status bar of each window, which only
/// chooses whether a change of the style animates. Never a wait. Main queue.
@interface ANNSystemUI : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

#pragma mark - Triggers

/// Every group: a screen appeared or disappeared, a tab, a menu, a modal or an overlay changed, a root was replaced.
+ (void)setNeedsUpdate;

/// The groups named.
+ (void)setNeedsUpdateOfGroups:(ANNSystemUIGroup)groups;

/// `mergeOptions` merged `options`: the groups they name are asked again, always (a merge is never dropped).
+ (void)optionsMerged:(ANNNavigationOptions *)options;

/// The groups `options` give a value to; for the status bar only the keys iOS reads (`visible`, `style`,
/// `hideWithTopBar`) when `onlyRead`, every key of `statusBar` otherwise.
+ (ANNSystemUIGroup)groupsNamedBy:(ANNNavigationOptions *)options onlyRead:(BOOL)onlyRead;

#pragma mark - Who decides

/// The screen the system UI of `node` follows: down `systemUIChild`, else `getCurrentChild`, to a node without a child.
/// A walk that meets a view controller that is no node of the layout stops there: nil.
+ (nullable UIViewController *)topScreenOf:(UIViewController *)node;

/// The controller UIKit takes the answer to `question` from in `window`, along UIKit's own chain: down the presented
/// controllers while UIKit hands the question to them (the status bar and the rest: a full-screen show as UIKit uses
/// it, or one that captures the status bar; the orientations: a show that covers the screen, `fullScreen` or
/// `overFullScreen`), then down the container's `childViewControllerFor…` to nil. Nil without a root.
+ (nullable UIViewController *)deciderInWindow:(nullable UIWindow *)window
                                       question:(ANNSystemUIQuestion)question;

/// The options `screen` answers the questions of `group` with: its resolved options; for a screen of an overlay, the
/// own options of its overlay's nodes from the overlay's root down to it, without the app's defaults, when they name a
/// key of the group; nil when they don't — the screen then answers what the main window's deciding controller answers
/// (`deciderInWindow:question:`).
+ (nullable ANNNavigationOptions *)optionsOfScreen:(UIViewController *)screen
                                          forGroup:(ANNSystemUIGroup)group;

/// Whether the bar of `navigation` is hidden, by `navigationBarHidden` or by UIKit's own hide of the bar (F6): what
/// `statusBar.hideWithTopBar` reads and what the stack watches. NO for nil.
+ (BOOL)barHiddenOf:(nullable UINavigationController *)navigation;

/// Whether the bar of the navigation controller that shows `screen` (the stack, or UIKit's More navigation) is hidden;
/// NO for a screen in no stack.
+ (BOOL)navigationBarHiddenOfScreen:(nullable UIViewController *)screen;

/// The node's own options over its parents' over the defaults, without what its children show: the tabs' answer while
/// UIKit's More list shows.
+ (ANNNavigationOptions *)ownOptionsWithDefaultOf:(UIViewController *)node;

@end

NS_ASSUME_NONNULL_END
