#import "ANNButtonComponentOptions.h"
#import "ANNFontOptions.h"
#import "ANNIconBackgroundOptions.h"
#import "ANNIconInsetsOptions.h"
#import "ANNMenuOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `OptionsTopBarButton`: a button of the top bar (`leftButtons`, `rightButtons`, `centerButtons`) or of the toolbar,
/// where spaces and the search field are buttons with a `systemItem` only.
@interface ANNButtonOptions : ANNOptions

/// `id`: the button in `ANN.NavigationButtonPressed`, and its `onPress` in JS.
@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) Bool *allCaps;
@property(nonatomic, strong) Image *icon;
/// The SF Symbol of `icon` given as `{ ios: '<name>' }`: JS moves the name here.
@property(nonatomic, strong) Text *sfSymbol;
@property(nonatomic, strong) ANNIconInsetsOptions *iconInsets;
@property(nonatomic, strong) ANNButtonComponentOptions *component;
@property(nonatomic, strong) Text *systemItem;
@property(nonatomic, strong) Text *text;
@property(nonatomic, strong) Text *accessibilityLabel;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;
@property(nonatomic, strong) Bool *enabled;
@property(nonatomic, strong) Bool *disableIconTint;
@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) Color *disabledColor;
@property(nonatomic, strong) ANNIconBackgroundOptions *iconBackground;
/// `'plain'` or `'prominent'`.
@property(nonatomic, strong) Text *style;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) Bool *hideSharedBackground;
@property(nonatomic, strong) Bool *showText;
@property(nonatomic, strong) Text *group;
/// A number, a text, a boolean, or `null`.
@property(nonatomic, strong) Param *badge;
@property(nonatomic, strong) Color *badgeColor;
@property(nonatomic, strong) Color *badgeTextColor;
@property(nonatomic, strong) Bool *toggle;
@property(nonatomic, strong) Bool *selected;
@property(nonatomic, strong) ANNMenuOptions *menu;
@property(nonatomic, strong) Bool *menuOnLongPress;
/// `'fixed'`, `'movable'` or `'optional'`.
@property(nonatomic, strong) Text *customization;
@property(nonatomic, strong) Bool *shownByDefault;
@property(nonatomic, strong) Bool *pinned;
/// `'high'`, `'standard'` or `'low'`.
@property(nonatomic, strong) Text *priority;
@property(nonatomic, strong) Bool *vertical;

@end

NS_ASSUME_NONNULL_END
