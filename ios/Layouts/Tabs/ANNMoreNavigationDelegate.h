#import <UIKit/UIKit.h>

@class ANNBottomTabsController;

NS_ASSUME_NONNULL_BEGIN

/// The delegate of UIKit's More navigation the tabs set: UIKit selects a tab picked in the More list without any
/// delegate of the tab bar, and its selection is already set when the More navigation is about to show the tab's
/// screen. The tabs hear `willShow` and `didShow` there; every message goes on to the delegate the More navigation had
/// before, so UIKit's own More keeps its behavior.
@interface ANNMoreNavigationDelegate : NSObject <UINavigationControllerDelegate>

- (instancetype)initWithTabs:(ANNBottomTabsController *)tabs
                    previous:(nullable id<UINavigationControllerDelegate>)previous;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The delegate the More navigation had before (kept weakly, as UIKit keeps its delegate).
@property(nonatomic, weak, readonly, nullable) id<UINavigationControllerDelegate> previous;

@end

NS_ASSUME_NONNULL_END
