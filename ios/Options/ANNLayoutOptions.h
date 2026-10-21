#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `layout` (`OptionsLayout`): the screen's place in the system, and `direction`, which JS puts into the default
/// options only.
@interface ANNLayoutOptions : ANNOptions

/// `'top'`, `'right'`, `'bottom'`, `'left'`; nil when unset, `[]` for none.
@property(nonatomic, strong, nullable) NSArray<NSString *> *safeAreaEdges;
@property(nonatomic, strong) Color *backgroundColor;
@property(nonatomic, strong) Color *componentBackgroundColor;
/// The orientations the screen allows; nil when unset.
@property(nonatomic, strong, nullable) NSArray<NSString *> *orientation;
@property(nonatomic, strong) Bool *verticalBars;
@property(nonatomic, strong) Bool *drawBehindVerticalBar;
@property(nonatomic, strong) Bool *autoHideHomeIndicator;
/// The edges whose system gestures the screen defers; nil when unset.
@property(nonatomic, strong, nullable) NSArray<NSString *> *deferSystemGestures;
@property(nonatomic, strong) Bool *adjustResize;
/// `'ltr'`, `'rtl'` or `'locale'`: the direction of the app, from the default options only.
@property(nonatomic, strong) Text *direction;

@end

NS_ASSUME_NONNULL_END
