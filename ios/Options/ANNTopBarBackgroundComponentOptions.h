#import "ANNComponentOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.background.component` (`OptionsTopBarBackgroundComponent`): the React component behind the bar. Its name
/// and id are read by `ANNComponentOptions`.
@interface ANNTopBarBackgroundComponentOptions : ANNComponentOptions

@property(nonatomic, strong) Bool *waitForRender;

@end

NS_ASSUME_NONNULL_END
