#import "ANNButtonOptions.h"
#import "ANNMenuOptions.h"
#import "ANNOptions.h"
#import "ANNSearchBarOptions.h"
#import "ANNTopBarBackButtonOptions.h"
#import "ANNTopBarBackgroundOptions.h"
#import "ANNTopBarLargeTitleOptions.h"
#import "ANNTopBarScrollEdgeAppearanceOptions.h"
#import "ANNTopBarSubtitleOptions.h"
#import "ANNTopBarTitleOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar` (`OptionsTopBar`): the bar of a screen in a stack.
@interface ANNTopBarOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Bool *animate;
@property(nonatomic, strong) Bool *hideOnScroll;
@property(nonatomic, strong) Bool *hideOnTap;
@property(nonatomic, strong) Bool *hideOnKeyboard;
@property(nonatomic, strong) Color *leftButtonColor;
@property(nonatomic, strong) Color *rightButtonColor;
@property(nonatomic, strong) Color *leftButtonBackgroundColor;
@property(nonatomic, strong) Color *rightButtonBackgroundColor;
@property(nonatomic, strong) Color *leftButtonDisabledColor;
@property(nonatomic, strong) Color *rightButtonDisabledColor;
@property(nonatomic, strong) Color *centerButtonColor;
@property(nonatomic, strong) Color *centerButtonDisabledColor;
@property(nonatomic, strong) Bool *drawBehind;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) ANNTopBarTitleOptions *title;
@property(nonatomic, strong) ANNTopBarSubtitleOptions *subtitle;
@property(nonatomic, strong) ANNTopBarBackButtonOptions *backButton;
/// Nil when the options have no list; `[]` takes the buttons of the layers under it away.
@property(nonatomic, strong, nullable) NSArray<ANNButtonOptions *> *leftButtons;
@property(nonatomic, strong, nullable) NSArray<ANNButtonOptions *> *rightButtons;
@property(nonatomic, strong, nullable) NSArray<ANNButtonOptions *> *centerButtons;
@property(nonatomic, strong) Text *customizationId;
@property(nonatomic, strong) ANNMenuOptions *overflowMenu;
/// `'tabs'` or `'buttons'`.
@property(nonatomic, strong) Text *verticalBarKeeps;
@property(nonatomic, strong) ANNTopBarBackgroundOptions *background;
@property(nonatomic, strong) ANNTopBarScrollEdgeAppearanceOptions *scrollEdgeAppearance;
/// `'default'` or `'black'`.
@property(nonatomic, strong) Text *barStyle;
/// `'navigator'`, `'browser'` or `'editor'`.
@property(nonatomic, strong) Text *style;
@property(nonatomic, strong) Bool *noBorder;
@property(nonatomic, strong) ANNSearchBarOptions *searchBar;
@property(nonatomic, strong) ANNTopBarLargeTitleOptions *largeTitle;
@property(nonatomic, strong) Double *height;
@property(nonatomic, strong) Color *borderColor;
@property(nonatomic, strong) Double *borderHeight;
@property(nonatomic, strong) Double *elevation;
@property(nonatomic, strong) Double *topMargin;
@property(nonatomic, strong) Bool *animateLeftButtons;
@property(nonatomic, strong) Bool *animateRightButtons;

@end

NS_ASSUME_NONNULL_END
