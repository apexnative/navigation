#import "ANNBottomTabsAnimationOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `EnterExitAnimationOptions` of the tab bar.
@interface ANNEnterExitBottomTabsAnimationOptions : ANNOptions

@property(nonatomic, strong) ANNBottomTabsAnimationOptions *enter;
@property(nonatomic, strong) ANNBottomTabsAnimationOptions *exit;

@end

NS_ASSUME_NONNULL_END
