#import "ANNComponentOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.title.component` (`OptionsTopBarTitleComponent`): the React component in the title, with how it sits in
/// the bar. Its name and id are read by `ANNComponentOptions`.
@interface ANNTopBarTitleComponentOptions : ANNComponentOptions

/// `'center'` or `'fill'`.
@property(nonatomic, strong) Text *alignment;
@property(nonatomic, strong) Bool *waitForRender;

@end

NS_ASSUME_NONNULL_END
