#import "ANNBottomTabDotIndicatorOptions.h"
#import "ANNComponentOptions.h"
#import "ANNFontOptions.h"
#import "ANNIconInsetsOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `bottomTab` (`OptionsBottomTab`): the options of one tab, set on its screen.
@interface ANNBottomTabOptions : ANNOptions

@property(nonatomic, strong) ANNBottomTabDotIndicatorOptions *dotIndicator;
@property(nonatomic, strong) ANNComponentOptions *component;
@property(nonatomic, strong) Text *text;
/// A text; `null` takes the badge away.
@property(nonatomic, strong) Text *badge;
@property(nonatomic, strong) Color *badgeColor;
@property(nonatomic, strong) Bool *animateBadge;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) Image *icon;
/// The name of an SF Symbol: the iOS side of `icon`, moved here by the engine.
@property(nonatomic, strong) Text *sfSymbol;
@property(nonatomic, strong) Color *iconColor;
@property(nonatomic, strong) Double *iconWidth;
@property(nonatomic, strong) Double *iconHeight;
@property(nonatomic, strong) Color *textColor;
@property(nonatomic, strong) Color *selectedIconColor;
@property(nonatomic, strong) Color *selectedTextColor;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;
@property(nonatomic, strong) Double *selectedFontSize;
@property(nonatomic, strong) ANNIconInsetsOptions *iconInsets;
@property(nonatomic, strong) Image *selectedIcon;
/// The name of an SF Symbol: the iOS side of `selectedIcon`, moved here by the engine.
@property(nonatomic, strong) Text *sfSelectedSymbol;
@property(nonatomic, strong) Bool *disableIconTint;
@property(nonatomic, strong) Bool *disableSelectedIconTint;
@property(nonatomic, strong) Bool *selectTabOnPress;
@property(nonatomic, strong) Bool *popToRootOnPress;
@property(nonatomic, strong) Bool *popToRootOnBlur;
/// `'search'`, `'bookmarks'`, `'contacts'`, `'downloads'`, `'favorites'`, `'featured'`, `'history'`, `'more'`,
/// `'mostRecent'`, `'mostViewed'`, `'recents'` or `'topRated'`.
@property(nonatomic, strong) Text *role;
/// `'fixed'`, `'movable'`, `'optional'`, `'pinned'` or `'sidebarOnly'`.
@property(nonatomic, strong) Text *customization;
@property(nonatomic, strong) Bool *shownByDefault;
@property(nonatomic, strong) Bool *allowsHiding;

@end

NS_ASSUME_NONNULL_END
