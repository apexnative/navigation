#import "ANNFontOptions.h"
#import "ANNIconBackgroundOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.backButton` (`OptionsTopBarBackButton`): the back button of the bar.
@interface ANNTopBarBackButtonOptions : ANNOptions

@property(nonatomic, strong) Text *accessibilityLabel;
@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) Image *icon;
/// The name of an SF Symbol: the iOS side of `icon`, moved here by the engine.
@property(nonatomic, strong) Text *sfSymbol;
@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Text *title;
@property(nonatomic, strong) Bool *showTitle;
@property(nonatomic, strong) Color *color;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;
@property(nonatomic, strong) ANNIconBackgroundOptions *iconBackground;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) Bool *enableMenu;
/// `'default'`, `'generic'` or `'minimal'`.
@property(nonatomic, strong) Text *displayMode;
@property(nonatomic, strong) Bool *hideSharedBackground;
@property(nonatomic, strong) Bool *popStackOnPress;

@end

NS_ASSUME_NONNULL_END
