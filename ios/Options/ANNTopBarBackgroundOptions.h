#import "ANNOptions.h"
#import "ANNTopBarBackgroundComponentOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.background` (`OptionsTopBarBackground`): what the bar is drawn on.
@interface ANNTopBarBackgroundOptions : ANNOptions

@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) Bool *clipToBounds;
@property(nonatomic, strong) ANNTopBarBackgroundComponentOptions *component;
@property(nonatomic, strong) Bool *translucent;
@property(nonatomic, strong) Bool *blur;

@end

NS_ASSUME_NONNULL_END
