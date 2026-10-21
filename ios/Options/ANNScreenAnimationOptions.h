#import "ANNViewAnimationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `ScreenAnimationOptions`: the animation of a screen that comes or goes.
@interface ANNScreenAnimationOptions : ANNViewAnimationOptions

@property(nonatomic, strong) Bool *enabled;
@property(nonatomic, strong) Bool *waitForRender;

@end

NS_ASSUME_NONNULL_END
