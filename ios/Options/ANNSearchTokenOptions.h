#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.searchBar.tokens[]` (`OptionsSearchBarToken`): a token before the text of the field.
@interface ANNSearchTokenOptions : ANNOptions

/// `id`.
@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) Text *text;
/// The SF Symbol of `icon` given as `{ ios: '<name>' }`: JS moves the name here.
@property(nonatomic, strong) Text *sfSymbol;
@property(nonatomic, strong) Image *icon;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END
