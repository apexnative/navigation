#import "ANNReactView.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The React background of a screen's bar (`topBar.background.component`). Each screen holds its own: it renders with
/// the screen and is alive when a pop comes back. It sits inside the bar's own background view (found by its class
/// name, among all the bar's subviews; without it in the bar at index 1) — over the system background, under the title,
/// buttons and back button — filling it, under the status bar too, and stretching with the bar. One at a time in a bar:
/// push, pop and the swipe fade the incoming one in and the old one out along the transition (with the finger on a
/// swipe; a cancelled swipe brings the old one back); no transition, the old one goes at once; a screen without one
/// takes the previous one away. The container takes a content view, never a class of the roots.
@interface ANNBarBackgroundComponent : NSObject

- (instancetype)initWithContent:(UIView<ANNComponentProtocol> *)content;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, strong, readonly) UIView<ANNComponentProtocol> *content;

/// `incoming` (nil: none) becomes the background of `bar`, along `coordinator` when one runs.
+ (void)show:(nullable ANNBarBackgroundComponent *)incoming
       inBar:(UINavigationBar *)bar
 coordinator:(nullable id<UIViewControllerTransitionCoordinator>)coordinator;

/// Whether this one is the background `bar` shows now.
- (BOOL)isShownInBar:(UINavigationBar *)bar;

/// It leaves any bar it is in (its screen goes).
- (void)leaveBar;

/// iOS 26 with a large title that folds into the list: the component is a fill at the bottom of the main list, under
/// its rows and the title, in `frame` (the list's coordinates); a bar that shows it again leaves it there (not taken
/// out at the end of a push or pop).
- (void)placeInList:(UIScrollView *)list frame:(CGRect)frame;

/// Whether the component is in a list now.
@property(nonatomic, readonly) BOOL inList;

/// Back from the list into `bar`'s background when the bar still shows it, else out of the list.
- (void)returnFromListToBar:(nullable UINavigationBar *)bar;

@end

NS_ASSUME_NONNULL_END
