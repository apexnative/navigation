#import "ANNOptions.h"
#import "ANNTopBarScrollEdgeAppearanceBackgroundOptions.h"
#import "ANNTopBarScrollEdgeAppearanceTitleOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.scrollEdgeAppearance` (`OptionsTopBarScrollEdgeAppearance`): the look of the bar when the content is at
/// its edge.
@interface ANNTopBarScrollEdgeAppearanceOptions : ANNOptions

@property(nonatomic, strong) ANNTopBarScrollEdgeAppearanceTitleOptions *title;
@property(nonatomic, strong) ANNTopBarScrollEdgeAppearanceBackgroundOptions *background;
@property(nonatomic, strong) Bool *active;
@property(nonatomic, strong) Bool *noBorder;
@property(nonatomic, strong) Color *borderColor;

@end

NS_ASSUME_NONNULL_END
