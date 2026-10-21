#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What shows takes new default options at once (`setDefaultOptions` steps 4 and 5). Main queue.
@interface ANNOptionsRepaint : NSObject

/// After `ANNDefaultOptions` stored `defaults`: the main window takes `window.backgroundColor` when the defaults set
/// it (the system background for "no color"; unset leaves the window as it is), then every node shown in a window of
/// the navigation applies its new resolved options (`-[ANNNodePresenter applyDefaultOptionsChange:]`). The layers are
/// the main window's root, the roots of the modals (`ANNModalCoordinator`) and the overlay windows' roots, bottom to
/// top, each walked depth first with the children before their parent, nodes out of a window left out. One Core
/// Animation transaction: the new look comes in one frame, also while a transition runs.
+ (void)applyNewDefaultOptions:(ANNNavigationOptions *)defaults;

@end

NS_ASSUME_NONNULL_END
