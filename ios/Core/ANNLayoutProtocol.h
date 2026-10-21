#import "ANNComponentViewCreator.h"
#import "ANNEventEmitter.h"
#import "ANNLayoutInfo.h"
#import "ANNNavigationOptions.h"
#import "ANNNodePresenter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What a wait for a node's render waits for on one of its waited screens (`waitedComponentViewControllers`).
typedef NS_ENUM(NSInteger, ANNWaitedReadiness) {
    /// The screen's content rendered: what the user sees.
    ANNWaitedReadinessContent,
    /// The screen's root mounted once: a screen the user doesn't see yet (an unselected tab of `together`), which
    /// renders its content only where it shows. Its part of a wait ends at the command's 0.5 s at the latest, and its
    /// bar components are not waited for.
    ANNWaitedReadinessRootMounted,
};

/// A transition of the navigation is done: what it moved is in place.
typedef void (^ANNTransitionCompletionBlock)(void);

/// A transition of the navigation could not be made: the sentence JavaScript gets ( no code), and the error behind it
/// when there is one.
typedef void (^ANNTransitionRejectionBlock)(NSString *message, NSError *_Nullable error);

/// What every node of the layout answers the core (the container contract). The core's containers (screen, stack, tabs)
/// and the container modules (split view, pair, side menu) adopt it. A node gets its data and its helper
/// (`ANNNodeHelper`) through `UIViewController+LayoutProtocol`, which also gives defaults safe for any view controller
/// (its view controller children, what its current child shows, destroying its children); a container answers the
/// methods that differ for it and does the common part with its helper, never through `super`.
@protocol ANNLayoutProtocol <NSObject>

@optional

/// The initializer of a container whose children come as a list.
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(nullable NSArray<UIViewController *> *)childViewControllers;

@required

/// The child the node shows now: the core resolves the node's options and finds its visible screen through it.
- (nullable UIViewController *)getCurrentChild;

/// Every child of the node, also those UIKit doesn't show now (a collapsed column, a screen of a pair out of the
/// window).
- (NSArray<UIViewController *> *)layoutChildren;

/// The screens of the node on screen now (the source of a popover, of a `zoom` modal).
- (NSArray<UIViewController *> *)shownComponentViewControllers;

/// Prepares the children for presentation; the node is ready once they are (`readyForPresentation`).
- (void)render;

/// The node, or a child of it, is ready: a node that waits for nothing else tells its helper.
- (void)readyForPresentation;

/// The node shows only once its content rendered (`waitForRender`).
- (BOOL)waitForRender;
- (void)setWaitForRender:(BOOL)waitForRender;

/// The node's options over those of its current child.
- (ANNNavigationOptions *)resolveOptions;

/// `mergeOptions` with the node's id: where the options go.
- (void)mergeOptions:(ANNNavigationOptions *)options;

/// A child merged `options`: what the node takes of them, and what goes on to its own parent.
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child;

/// The node goes: the React views of its screens stop.
- (void)destroyReactView;

/// The node goes, with everything in it.
- (void)destroy;

/// The node left its stack: its own cleanup. It sends no event: the core sends `ANN.ScreenPopped` itself, for the
/// screens of `screensLeavingWithNode`, or for the node alone without that capability.
- (void)screenPopped;

/// The child the system UI follows: the home indicator, the deferred system gestures, the vertical bars.
- (nullable UIViewController *)systemUIChild;

@optional

#pragma mark - The waits

/// The screens a wait for the node's render waits for (a root's `setRoot`, a modal's, an overlay's): by default the
/// shown screens (`shownComponentViewControllers`), and a container whose shown screens are all inside its current
/// child answers what that child answers (`UIViewController+LayoutProtocol`). A node whose waited screens differ from
/// what it shows answers itself (the tabs, with the readiness each screen needs: `waitedReadinessOfScreen:`).
- (NSArray<UIViewController *> *)waitedComponentViewControllers;

/// The readiness a wait needs of `screen`, a waited screen inside this node; a node without an answer leaves it to its
/// parents (`ANNWaitedReadinessOf`). The tabs answer "root mounted" for the screens of their unselected tabs.
- (ANNWaitedReadiness)waitedReadinessOfScreen:(UIViewController *)screen;

#pragma mark - The children

/// A child of the node disappeared (UIKit's `viewDidDisappear:` of a stack): the tabs check `popToRootOnBlur` of its
/// tab there.
- (void)childDidDisappear:(UIViewController *)child;

/// A screen inside the node appeared in UIKit while a command drove its appearance (`ANNAppearanceDispatcher`). The
/// screen tells every layout parent up its chain; a node that drives a switch of its own ends it here when the screen
/// is the one it waited for (the tabs), and ends no other node's switch.
- (void)drivenDescendantDidAppear:(UIViewController *)screen;

/// A screen inside the node appeared in UIKit by itself, no command driving its appearance (a pop of UIKit's own: the
/// back button, the edge swipe). The screen tells every layout parent up its chain. A split view whose columns UIKit
/// shows as one stack announces what it shows then: UIKit took a column off that stack by itself.
- (void)descendantDidAppear:(UIViewController *)screen;

#pragma mark - Capabilities: a node without one answers "no"

/// `returnsScreenFromColumn`: `pop` of the only screen of a column goes back to the column before it (split view). YES
/// when the node did it; `completion` runs once that is on screen.
- (BOOL)goBackFromColumnOf:(UIViewController *)screen completion:(dispatch_block_t)completion;

/// `screensLeavingWithNode`: the screens that leave their stack with the node (a pair: both); the core sends
/// `ANN.ScreenPopped` for each of them.
- (NSArray<UIViewController *> *)screensLeavingWithNode;

/// `childOffsetFromRest`: how far the child is moved from where it rests (a sliding pane), for its safe area.
- (CGPoint)childOffsetFromRest:(UIViewController *)child;

/// `optionsScreen`: the screen whose options are the node's (a pair: the primary): its bars, search, status bar.
- (nullable UIViewController *)optionsScreen;

/// `keepsStatusBarRoom`: the node keeps the room of the status bar for its screens (a pair).
- (BOOL)keepsStatusBarRoom;

#pragma mark - The columns of a split view

/// The view controller of the column: 'primary', 'supplementary', 'secondary', 'compact' or 'inspector'.
- (nullable UIViewController *)viewControllerOfColumn:(NSString *)column;

/// Collapsed into the compact layout: no column shows.
@property(nonatomic, readonly) BOOL isCollapsedIntoCompact;

/// `showInColumn`: `screen` in place of what the column shows.
- (void)showScreen:(UIViewController *)screen
              inColumn:(NSString *)column
    fromViewController:(UIViewController *)fromViewController
            completion:(ANNTransitionCompletionBlock)completion
             rejection:(ANNTransitionRejectionBlock)rejection;

@end

NS_ASSUME_NONNULL_END
