#import "ANNElementAnimationOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `ElementTransition`: the animations of the element with this `id`.
@interface ANNElementTransitionOptions : ANNOptions

@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) ANNElementAnimationOptions *alpha;
@property(nonatomic, strong) ANNElementAnimationOptions *x;
@property(nonatomic, strong) ANNElementAnimationOptions *y;
@property(nonatomic, strong) ANNElementAnimationOptions *translationX;
@property(nonatomic, strong) ANNElementAnimationOptions *translationY;
@property(nonatomic, strong) ANNElementAnimationOptions *scaleX;
@property(nonatomic, strong) ANNElementAnimationOptions *scaleY;
@property(nonatomic, strong) ANNElementAnimationOptions *rotationX;
@property(nonatomic, strong) ANNElementAnimationOptions *rotationY;
@property(nonatomic, strong) ANNElementAnimationOptions *rotation;

/// The ten properties in the order of `-[ANNViewAnimationOptions animatedProperties]`.
- (NSArray<ANNElementAnimationOptions *> *)animatedProperties;

@end

NS_ASSUME_NONNULL_END
