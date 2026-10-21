#import "ANNInterpolationOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `SharedElementTransition`: the flight of an element from `fromId` to `toId`.
@interface ANNSharedElementTransitionOptions : ANNOptions

@property(nonatomic, strong) Text *fromId;
@property(nonatomic, strong) Text *toId;
@property(nonatomic, strong) Double *duration;
@property(nonatomic, strong) Double *startDelay;
@property(nonatomic, strong) ANNInterpolationOptions *interpolation;

@end

NS_ASSUME_NONNULL_END
