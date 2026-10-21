#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `IconInsets`: the insets of an icon, in points. Each side stands alone: a side left out takes the option's own
/// default, not 0.
@interface ANNIconInsetsOptions : ANNOptions

@property(nonatomic, strong) Double *top;
@property(nonatomic, strong) Double *left;
@property(nonatomic, strong) Double *bottom;
@property(nonatomic, strong) Double *right;

@end

NS_ASSUME_NONNULL_END
