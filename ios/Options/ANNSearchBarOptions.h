#import "ANNComponentOptions.h"
#import "ANNOptions.h"
#import "ANNSearchScopeOptions.h"
#import "ANNSearchSuggestionOptions.h"
#import "ANNSearchTokenOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.searchBar.placement`: where the field is.
typedef NS_ENUM(NSInteger, SearchBarPlacement) {
    SearchBarPlacementAutomatic,
    SearchBarPlacementStacked,
    SearchBarPlacementIntegrated,
    SearchBarPlacementIntegratedButton,
    SearchBarPlacementIntegratedCentered,
};

/// `topBar.searchBar` (`OptionsSearchBar`): the search of a screen. `ANNSearchPresenter` applies it. The scope the user
/// picks, the tokens the user deletes or brings back and the suggestions UIKit removes go into the screen's user layer
/// (`setUserChoice:forKey:`), not into these options: they stay when the options apply again and through a
/// `mergeOptions` that sends the same values, and give way when a merge changes the field.
@interface ANNSearchBarOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Bool *focus;
@property(nonatomic, strong) Bool *hideOnScroll;
@property(nonatomic, strong) Bool *hideTopBarOnFocus;
@property(nonatomic, strong) Bool *obscuresBackgroundDuringPresentation;
@property(nonatomic, strong) Color *backgroundColor;
@property(nonatomic, strong) Color *tintColor;
@property(nonatomic, strong) Text *placeholder;
@property(nonatomic, strong) Text *text;
/// A `SearchBarPlacement`.
@property(nonatomic, strong) ANNIntegerParam *placement;
@property(nonatomic, strong, nullable) NSArray<ANNSearchScopeOptions *> *scopes;
@property(nonatomic, strong) Text *selectedScope;
/// `'automatic'`, `'onTextEntry'` or `'onSearchActivation'`.
@property(nonatomic, strong) Text *scopeBarActivation;
/// Nil when the options have no `tokens`.
@property(nonatomic, strong, nullable) NSArray<ANNSearchTokenOptions *> *tokens;
/// Nil when the options have no `suggestions`.
@property(nonatomic, strong, nullable) NSArray<ANNSearchSuggestionOptions *> *suggestions;
@property(nonatomic, strong) ANNComponentOptions *resultsComponent;
/// `'onSearchActivation'` or `'onTextEntry'`.
@property(nonatomic, strong) Text *resultsActivation;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END
