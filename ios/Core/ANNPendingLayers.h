#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The layers that wait to show: a root a `setRoot` puts up while it waits (for its render, then for its turn at the
/// presentation gate); a modal or an overlay that waits. Such a layer sits in the window, unseen. A node inside it
/// counts as off screen, and what changes what the layer shows tells the layer's wait, so that the wait follows a top a
/// command changed. The layer's owner adds it when it starts to wait in the window and removes it when it shows or
/// goes; nodes only ask. Main queue.
@interface ANNPendingLayers : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `layer` waits to show from now on. `onShownScreensChanged` runs each time what a node inside it shows changed
/// (`shownScreensChangedIn:`); the layer keeps it until it is removed. Adding a layer again replaces its handler.
+ (void)add:(UIViewController *)layer onShownScreensChanged:(dispatch_block_t)onShownScreensChanged;

/// `layer` waits to show no more; nothing when it doesn't.
+ (void)remove:(UIViewController *)layer;

/// Whether `node`, or a layout parent of it, is a layer that waits to show.
+ (BOOL)isWaitingToShow:(UIViewController *)node;

/// What `node` shows changed (a stack command ran in it): the nearest layer that waits to show and holds it hears it.
/// Nothing when no such layer holds it.
+ (void)shownScreensChangedIn:(UIViewController *)node;

@end

NS_ASSUME_NONNULL_END
