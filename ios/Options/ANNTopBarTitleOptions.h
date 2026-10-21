#import "ANNFontOptions.h"
#import "ANNMenuOptions.h"
#import "ANNOptions.h"
#import "ANNTopBarTitleComponentOptions.h"
#import "ANNTopBarTitleDocumentOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.title` (`OptionsTopBarTitle`): the title of the bar.
@interface ANNTopBarTitleOptions : ANNOptions

@property(nonatomic, strong) Text *text;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;
@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) ANNTopBarTitleComponentOptions *component;
@property(nonatomic, strong) ANNMenuOptions *menu;
@property(nonatomic, strong) Bool *rename;
@property(nonatomic, strong) ANNTopBarTitleDocumentOptions *document;
/// `'regular'`, `'clear'` or `'none'`.
@property(nonatomic, strong) Text *glass;
@property(nonatomic, strong) Double *height;
@property(nonatomic, strong) Double *topMargin;
/// `'center'` or `'fill'`.
@property(nonatomic, strong) Text *alignment;

@end

NS_ASSUME_NONNULL_END
