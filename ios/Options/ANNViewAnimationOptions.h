#import "ANNAnimationPropertyOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `ViewAnimationOptions`: the animated properties of a view.
@interface ANNViewAnimationOptions : ANNOptions

@property(nonatomic, strong) ANNAnimationPropertyOptions *x;
@property(nonatomic, strong) ANNAnimationPropertyOptions *y;
@property(nonatomic, strong) ANNAnimationPropertyOptions *translationX;
@property(nonatomic, strong) ANNAnimationPropertyOptions *translationY;
@property(nonatomic, strong) ANNAnimationPropertyOptions *alpha;
@property(nonatomic, strong) ANNAnimationPropertyOptions *scaleX;
@property(nonatomic, strong) ANNAnimationPropertyOptions *scaleY;
@property(nonatomic, strong) ANNAnimationPropertyOptions *rotationX;
@property(nonatomic, strong) ANNAnimationPropertyOptions *rotationY;
@property(nonatomic, strong) ANNAnimationPropertyOptions *rotation;

/// The ten properties in one order, the one every reader uses: `x`, `y`, `translationX`, `translationY`, `alpha`,
/// `scaleX`, `scaleY`, `rotationX`, `rotationY`, `rotation`.
- (NSArray<ANNAnimationPropertyOptions *> *)animatedProperties;

@end

NS_ASSUME_NONNULL_END
