#import "ANNStackAnimationOptions.h"
#import "ANNZoomTransitionOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `animations.push` (`StackPushAnimationOptions`).
@interface ANNStackPushAnimationOptions : ANNStackAnimationOptions

@property(nonatomic, strong) ANNZoomTransitionOptions *zoom;

@end

NS_ASSUME_NONNULL_END
