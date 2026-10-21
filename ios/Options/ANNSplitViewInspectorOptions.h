#import "ANNSplitViewColumnOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `splitView.inspector` (`OptionsSplitViewInspector`, iPadOS 26): the widths of the inspector column, and whether it
/// shows.
@interface ANNSplitViewInspectorOptions : ANNSplitViewColumnOptions

@property(nonatomic, strong) Bool *visible;

@end

NS_ASSUME_NONNULL_END
