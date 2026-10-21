#import "ANNOptions.h"
#import "ANNSplitViewColumnOptions.h"
#import "ANNSplitViewInspectorOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `splitView` (`OptionsSplitView`): the options of a split view. The strings are the values of UIKit's enumerations as
/// JS gives them, nil when unset or unknown; `displayMode` keeps any string (the module maps the earlier names and
/// gives the rest `automatic`). `ANNSplitViewPresenter` applies them through the split view module's category.
@interface ANNSplitViewOptions : ANNOptions

@property(nonatomic, copy, nullable) NSString *displayMode;
@property(nonatomic, copy, nullable) NSString *primaryEdge;
@property(nonatomic, strong) Number *width;
@property(nonatomic, strong) Number *minWidth;
@property(nonatomic, strong) Number *maxWidth;
@property(nonatomic, copy, nullable) NSString *primaryBackgroundStyle;
@property(nonatomic, strong) ANNSplitViewColumnOptions *supplementary;
@property(nonatomic, strong) ANNSplitViewInspectorOptions *inspector;
@property(nonatomic, copy, nullable) NSString *splitBehavior;
@property(nonatomic, copy, nullable) NSString *displayModeButton;
@property(nonatomic, strong) Bool *presentsWithGesture;
@property(nonatomic, strong) Bool *showsSecondaryOnlyButton;
@property(nonatomic, copy, nullable) NSString *compactTopColumn;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END
