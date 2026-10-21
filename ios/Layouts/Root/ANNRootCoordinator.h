#import "ANNCommandAnswer.h"
#import "ANNLayoutRoot.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The root of the navigation's window and the `setRoot` that is putting up a new one ( `setRoot`). At most one
/// `setRoot` is pending: waiting for its root to render (and for a presentation running in the window, patch 618), or
/// in its transition. A newer one supersedes it (last wins): no promise hangs and no old root stays under a new one.
/// While it waits, the root of the pending `setRoot` is a layer that waits to show (`ANNPendingLayers`). Main queue.
@interface ANNRootCoordinator : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `setRoot` after the runner's shared steps: the options of the trees, "shows already", the types the factory makes,
/// the pending `setRoot` superseded, the root made and rendered in the window's bounds, the wait for its render, then
/// its transition, and the answer with the root's id.
+ (void)setRoot:(ANNLayoutRoot *)layout answer:(ANNCommandAnswer *)answer;

/// Whether `viewController`, as the window's root, is the splash: the copy of the launch screen, the blank screen after
/// a reload, or no root view controller at all.
+ (BOOL)isSplash:(nullable UIViewController *)viewController;

/// The coordinator of a presentation or dismissal running over `root`: of a view controller of its presented chain
/// that is being presented or dismissed (a containment transition, as a push, doesn't count). Nil when none runs.
+ (nullable id<UIViewControllerTransitionCoordinator>)runningPresentationTransitionOver:
    (nullable UIViewController *)root;

/// The main window's root plays its own `setRoot` animation now: its view carries the animation's transform and alpha
/// until the end, so the system UI's owner holds the orientation until then.
+ (BOOL)rootAnimationRuns;

/// The reload reset (`ANNReloadCoordinator`): the pending `setRoot` of the old JavaScript ends without an answer, and
/// the nodes only this coordinator holds come back to be released with the other old screens: a root still waiting to
/// render, and the root a running transition replaces (its snapshot goes at once). The window's own root is not among
/// them: the reset takes it from the window.
+ (NSArray<UIViewController *> *)takeNodesForReload;

@end

NS_ASSUME_NONNULL_END
