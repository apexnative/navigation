#import "ANNOptions.h"
#import "ANNScrollEdgeOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `scrollEdgeEffect` (`OptionsScrollEdgeEffect`): the effect at the edges of a scrolling content, for all edges and
/// for each one.
@interface ANNScrollEdgeEffectOptions : ANNOptions

@property(nonatomic, strong) Bool *hidden;
/// `'automatic'`, `'soft'` or `'hard'`.
@property(nonatomic, strong) Text *style;
@property(nonatomic, strong) ANNScrollEdgeOptions *top;
@property(nonatomic, strong) ANNScrollEdgeOptions *bottom;
@property(nonatomic, strong) ANNScrollEdgeOptions *left;
@property(nonatomic, strong) ANNScrollEdgeOptions *right;

@end

NS_ASSUME_NONNULL_END
