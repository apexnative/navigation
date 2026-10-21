#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The one seam of "what a node shows changed": a stack operation ended, a tab was chosen, a side menu's menu showed or
/// hid, a split view's columns collapsed, expanded or changed their display mode, a column took a new screen
/// (`showInColumn`), a pair changed its arrangement. Every container that changes what it shows calls only this; the
/// seam tells, in this order:
///
/// - the layer that waits to show and holds the node (`ANNPendingLayers`): its wait takes its screens again;
/// - the modal that holds the node (`ANNModalCoordinator`): its hold follows the screen it shows now;
/// - the system UI (`ANNSystemUI`): UIKit is asked again at the end of the turn.
///
/// Each of them does nothing when nothing changed for it, so a call that changes nothing costs nothing. The height of a
/// sheet or a modal sized to its content is not part of it: it follows a stack's top, and stays the stack's own call
/// (`+[ANNSheetPresenter stackDidShowScreenInModal:]`). Main queue.
@interface ANNShownScreens : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// What `node` shows changed.
+ (void)changedIn:(UIViewController *)node;

@end

NS_ASSUME_NONNULL_END
