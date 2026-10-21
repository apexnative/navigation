#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.searchBar.scopes[]` (`OptionsSearchBarScope`): a scope of the search.
@interface ANNSearchScopeOptions : ANNOptions

/// `id`.
@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) Text *title;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END
