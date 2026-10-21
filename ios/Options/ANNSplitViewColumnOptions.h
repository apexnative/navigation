#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// The widths of a column of a split view (`OptionsSplitViewColumn`): `splitView.supplementary`.
@interface ANNSplitViewColumnOptions : ANNOptions

@property(nonatomic, strong) Number *width;
@property(nonatomic, strong) Number *minWidth;
@property(nonatomic, strong) Number *maxWidth;

@end

NS_ASSUME_NONNULL_END
