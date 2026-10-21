#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `scrollEdgeEffect.top`, `.bottom`, `.left`, `.right` (`OptionsScrollEdge`): the effect of one edge.
@interface ANNScrollEdgeOptions : ANNOptions

@property(nonatomic, strong) Bool *hidden;
/// `'automatic'`, `'soft'` or `'hard'`.
@property(nonatomic, strong) Text *style;

@end

NS_ASSUME_NONNULL_END
