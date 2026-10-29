#import "ANNStackAnimationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `animations.pop` (`StackPopAnimationOptions`).
@interface ANNStackPopAnimationOptions : ANNStackAnimationOptions

/// The swipe back plays the screen's own pop under the finger.
@property(nonatomic, strong) Bool *interactive;

@end

NS_ASSUME_NONNULL_END
