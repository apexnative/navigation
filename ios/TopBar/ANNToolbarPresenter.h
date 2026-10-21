#import "ANNBarButtons.h"
#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The screen's part of the stack's toolbar (`toolbar.*`): the screen's `toolbarItems` from `toolbar.buttons` in the
/// format of bar buttons, set without an animation at the screen's appearance and animated in merges. The stack shows
/// or hides the toolbar by the top screen's options (`ANNStackPresenter`); the tab bar is not touched.
@interface ANNToolbarPresenter : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The items of `options` into `screen`; a screen in a pair tells its pair (`screen:didSetToolbarItemsAnimated:`).
+ (void)applyOptions:(ANNNavigationOptions *)options
            ofScreen:(UIViewController *)screen
             buttons:(ANNBarButtons *)buttons
            animated:(BOOL)animated;

/// Whether a merge changes the toolbar: the module's group has no `hasValue` of its own (correction 14).
+ (BOOL)mergeChangesToolbar:(ANNNavigationOptions *)mergeOptions;

@end

NS_ASSUME_NONNULL_END
