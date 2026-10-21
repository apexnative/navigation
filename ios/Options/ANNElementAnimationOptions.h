#import "ANNInterpolationOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `ElementAnimation` (`AppearingElementAnimation`, `DisappearingElementAnimation`): the animation of one property of
/// an element. A union in TS, one group here: an appearing one has `from`, a disappearing one has `to`.
@interface ANNElementAnimationOptions : ANNOptions

@property(nonatomic, strong) Double *from;
@property(nonatomic, strong) Double *to;
@property(nonatomic, strong) Double *duration;
@property(nonatomic, strong) Double *startDelay;
@property(nonatomic, strong) ANNInterpolationOptions *interpolation;

@end

NS_ASSUME_NONNULL_END
