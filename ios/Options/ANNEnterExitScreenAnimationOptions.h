#import "ANNOptions.h"
#import "ANNScreenAnimationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `EnterExitAnimationOptions`: the animations of a screen that comes (`enter`) and one that goes (`exit`).
@interface ANNEnterExitScreenAnimationOptions : ANNOptions

@property(nonatomic, strong) ANNScreenAnimationOptions *enter;
@property(nonatomic, strong) ANNScreenAnimationOptions *exit;

@end

NS_ASSUME_NONNULL_END
