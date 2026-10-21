#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.scrollEdgeAppearance.background` (`OptionsTopBarScrollEdgeAppearanceBackground`): the background when the
/// content is at the edge of the bar.
@interface ANNTopBarScrollEdgeAppearanceBackgroundOptions : ANNOptions

@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) Bool *translucent;

@end

NS_ASSUME_NONNULL_END
