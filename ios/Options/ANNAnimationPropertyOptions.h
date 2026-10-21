#import "ANNInterpolationOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `OptionsAnimationPropertyConfig`: the animation of one property of a view.
@interface ANNAnimationPropertyOptions : ANNOptions

@property(nonatomic, strong) Double *from;
@property(nonatomic, strong) Double *to;
@property(nonatomic, strong) Double *duration;
@property(nonatomic, strong) Double *startDelay;
@property(nonatomic, strong) ANNInterpolationOptions *interpolation;

@end

NS_ASSUME_NONNULL_END
