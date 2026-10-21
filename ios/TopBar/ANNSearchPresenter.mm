#import "ANNSearchPresenter.h"
#import "ANNEventEmitter.h"
#import "ANNScreenInsets.h"
#import "ANNSearchResultsViewController.h"

static void *ANNSearchFieldColorContext = &ANNSearchFieldColorContext;

// Before iOS 26 the integrated placements are the inline one of iOS 16.
static UINavigationItemSearchBarPlacement ANNNavigationItemSearchBarPlacement(SearchBarPlacement placement)
    API_AVAILABLE(ios(16.0)) {
    switch (placement) {
    case SearchBarPlacementAutomatic:
        return UINavigationItemSearchBarPlacementAutomatic;
    case SearchBarPlacementStacked:
        return UINavigationItemSearchBarPlacementStacked;
    case SearchBarPlacementIntegrated:
    case SearchBarPlacementIntegratedButton:
    case SearchBarPlacementIntegratedCentered:
        break;
    }
    if (@available(iOS 26.0, *)) {
        switch (placement) {
        case SearchBarPlacementIntegratedButton:
            return UINavigationItemSearchBarPlacementIntegratedButton;
        case SearchBarPlacementIntegratedCentered:
            return UINavigationItemSearchBarPlacementIntegratedCentered;
        default:
            return UINavigationItemSearchBarPlacementIntegrated;
        }
    }
    return UINavigationItemSearchBarPlacementInline;
}

static UISearchControllerScopeBarActivation ANNScopeBarActivation(NSString *activation) API_AVAILABLE(ios(16.0)) {
    if ([activation isEqualToString:@"onTextEntry"]) {
        return UISearchControllerScopeBarActivationOnTextEntry;
    }
    if ([activation isEqualToString:@"onSearchActivation"]) {
        return UISearchControllerScopeBarActivationOnSearchActivation;
    }
    return UISearchControllerScopeBarActivationAutomatic;
}

// The results component as UIKit sees it: another one needs another search controller.
static NSString *ANNResultsKey(ANNComponentOptions *component) {
    if (!component.name.hasValue || !component.componentId.hasValue) {
        return nil;
    }
    return [NSString stringWithFormat:@"%@\n%@", component.name.get, component.componentId.get];
}

@interface ANNSearchPresenter () <UISearchResultsUpdating, UISearchBarDelegate, UISearchControllerDelegate>
@end

@implementation ANNSearchPresenter {
    __weak UIViewController<ANNLayoutProtocol> *_viewController;
    ANNReactComponentRegistry *_componentRegistry;
    // resultsComponent: UIKit takes the results controller only when the search controller is created, so another
    // component creates the search controller again.
    NSString *_resultsKey;
    NSString *_resultsComponentId;
    // The results component shows as soon as the search opens, not only while the field has text.
    BOOL _resultsOnSearchActivation;
    BOOL _didApplyOptions;
    BOOL _visible;
    // `focus` waits for the screen to be shown: the options it was created with, or a mergeOptions before it showed.
    BOOL _focusWhenShown;
    // hideOnScroll of a search bar created for a screen not shown yet waits for the screen to be shown: with
    // hidesSearchBarWhenScrolling set before that, UIKit shows the screen with the search bar already hidden.
    BOOL _hidesWhenScrolling;
    BOOL _hideOnScrollWaits;
    // backgroundColor: under a large title UIKit sets its own color on the field whenever the title expands or
    // collapses (a transparent one when it is collapsed), so the field is observed and gets the option's color back.
    // UIKit's last color returns when the option is removed.
    UISearchTextField *_observedField;
    UIColor *_fieldColor;
    UIColor *_systemFieldColor;
    BOOL _settingFieldColor;
    // The ids of the scope bar segments, in their order.
    NSArray<NSString *> *_scopeIds;
    // tokens: what the field shows, and every token the options had, by id, so a token the user deletes and brings
    // back with Undo is still known.
    NSArray<NSString *> *_appliedTokens;
    NSMutableDictionary<NSString *, ANNSearchTokenOptions *> *_tokenOptions;
    // suggestions: what the search shows. UIKit removes them when the user picks one or goes on searching.
    NSArray<NSString *> *_appliedSuggestions;
    // The toolbar took the search field or gave it back while the search was open: its bar is placed again when the
    // search closes.
    BOOL _placeWhenDismissed;
}

- (instancetype)initWithComponentRegistry:(ANNReactComponentRegistry *)componentRegistry {
    self = [super init];
    _componentRegistry = componentRegistry;
    return self;
}

- (void)dealloc {
    [self stopObservingField];
}

- (void)bindViewController:(UIViewController<ANNLayoutProtocol> *)viewController {
    _viewController = viewController;
}

- (void)applyOptions:(ANNSearchBarOptions *)options {
    BOOL first = !_didApplyOptions;
    if (first) {
        _didApplyOptions = YES;
        _focusWhenShown = [options.focus withDefault:NO];
    }
    if ([options.visible withDefault:NO]) {
        [self showSearchBar:options];
        // The text of the options once, as the screen is created: applied again on each appearance, it would take the
        // place of what the user typed.
        if (first && options.text.hasValue) {
            [self setText:options.text.get];
        }
    } else if (_viewController.navigationItem.searchController.searchResultsUpdater == self) {
        // Options that turned the search off (the defaults changed) remove the search this module made; a search
        // controller the app set on a native screen's item stays.
        [self removeSearchBar];
    }
}

- (void)mergeOptions:(ANNSearchBarOptions *)options resolvedOptions:(ANNSearchBarOptions *)resolvedOptions {
    if (!options.hasValue) {
        return;
    }
    if (![resolvedOptions.visible withDefault:NO]) {
        // Only the search this module made: a search controller the app set on a native screen's item stays.
        if (_viewController.navigationItem.searchController.searchResultsUpdater == self) {
            [self removeSearchBar];
        }
        return;
    }
    [self showSearchBar:resolvedOptions];
    if (options.text.hasValue) {
        [self setText:options.text.get];
    }
    if (options.focus.hasValue) {
        [self setFocused:options.focus.get];
    }
}

// searchBar.text: the app sets the query, so no searchBarUpdated follows; the field keeps its cursor when the text is
// the same.
- (void)setText:(NSString *)text {
    UISearchBar *searchBar = _viewController.navigationItem.searchController.searchBar;
    if (![searchBar.text isEqualToString:text]) {
        searchBar.text = text;
    }
}

- (void)componentDidAppear {
    _visible = YES;
    if (_hideOnScrollWaits) {
        _hideOnScrollWaits = NO;
        _viewController.navigationItem.hidesSearchBarWhenScrolling = _hidesWhenScrolling;
    }
    if (_focusWhenShown) {
        [self setFocused:YES];
    }
}

- (void)componentDidDisappear {
    _visible = NO;
}

// Every option applies whenever the options do, so mergeOptions changes the search bar as it is.
- (void)showSearchBar:(ANNSearchBarOptions *)options {
    UIViewController<ANNLayoutProtocol> *viewController = _viewController;
    UINavigationItem *navigationItem = viewController.navigationItem;
    UISearchController *search = navigationItem.searchController;
    NSString *resultsKey = ANNResultsKey(options.resultsComponent);
    if (search && ![(_resultsKey ?: @"") isEqualToString:(resultsKey ?: @"")]) {
        [self removeSearchBar];
        search = nil;
    }
    if (!search) {
        search = [[UISearchController alloc] initWithSearchResultsController:[self resultsController:options]];
        search.searchResultsUpdater = self;
        search.delegate = self;
        search.searchBar.delegate = self;
        search.searchBar.searchBarStyle = UISearchBarStyleProminent;
        _hideOnScrollWaits = !_visible;
        navigationItem.searchController = search;
        // Otherwise UIKit takes the root view controller as the presentation context of the search.
        viewController.definesPresentationContext = YES;
    }
    search.obscuresBackgroundDuringPresentation = [options.obscuresBackgroundDuringPresentation withDefault:NO];
    search.hidesNavigationBarDuringPresentation = [options.hideTopBarOnFocus withDefault:NO];
    // nil would remove the system placeholder too.
    if (options.placeholder.hasValue) {
        search.searchBar.placeholder = options.placeholder.get;
    }
    search.searchBar.tintColor = [options.tintColor withDefault:nil];
    // The id of the search field; its cancel and clear buttons, scopes, tokens and suggestions are UIKit's own views
    // without a public way to name them: found by their text.
    search.searchBar.searchTextField.accessibilityIdentifier = [options.testID withDefault:nil];
    [self applyFieldColor:options.backgroundColor field:search.searchBar.searchTextField];
    [self applyResultsActivation:options search:search];
    [self applyScopes:options search:search];
    [self applyTokens:options.tokens field:search.searchBar.searchTextField];
    if (@available(iOS 16.0, *)) {
        [self applySuggestions:options.suggestions search:search];
    }
    _hidesWhenScrolling = [options.hideOnScroll withDefault:NO];
    navigationItem.hidesSearchBarWhenScrolling = !_hideOnScrollWaits && _hidesWhenScrolling;
    if (@available(iOS 16.0, *)) {
        navigationItem.preferredSearchBarPlacement =
            ANNNavigationItemSearchBarPlacement(
                (SearchBarPlacement)[options.placement withDefault:SearchBarPlacementStacked]);
    }
}

// UIKit decides where the search bar goes (under the title, at the bottom, in the toolbar) when the search controller
// is set: set again, as at the screen's creation, it follows the toolbar items as they are now. The placement is set
// again with it, and every other state stays in the controller and the item.
- (void)placeSearchBarAgain {
    UINavigationItem *navigationItem = _viewController.navigationItem;
    UISearchController *search = navigationItem.searchController;
    if (!search || search.searchResultsUpdater != self) {
        _placeWhenDismissed = NO;
        return;
    }
    if (search.active) {
        _placeWhenDismissed = YES;
        return;
    }
    _placeWhenDismissed = NO;
    // As at the creation: a screen not shown gets the search bar shown, `hideOnScroll` from its appearance on; a shown
    // screen keeps the item's value.
    if (!_visible && _hidesWhenScrolling) {
        _hideOnScrollWaits = YES;
        navigationItem.hidesSearchBarWhenScrolling = NO;
    }
    UINavigationItemSearchBarPlacement placement = navigationItem.preferredSearchBarPlacement;
    navigationItem.searchController = nil;
    navigationItem.searchController = search;
    navigationItem.preferredSearchBarPlacement = placement;
    // UIKit keeps the bar where the toolbar had it until the toolbar's say in it changes: the flag back and forth
    // makes it place the bar again from the toolbar items as they are now.
    BOOL allowsToolbar = navigationItem.searchBarPlacementAllowsToolbarIntegration;
    navigationItem.searchBarPlacementAllowsToolbarIntegration = !allowsToolbar;
    navigationItem.searchBarPlacementAllowsToolbarIntegration = allowsToolbar;
}

// An active search is closed first, so its keyboard and presentation go with it.
- (void)removeSearchBar {
    UINavigationItem *navigationItem = _viewController.navigationItem;
    _focusWhenShown = NO;
    _placeWhenDismissed = NO;
    _hideOnScrollWaits = NO;
    _appliedTokens = nil;
    _appliedSuggestions = nil;
    if (_resultsComponentId) {
        [_componentRegistry removeChildComponent:_resultsComponentId];
        [ANNScreenInsets removeScreenId:_resultsComponentId];
    }
    _resultsKey = nil;
    _resultsComponentId = nil;
    [self stopObservingField];
    navigationItem.searchController.active = NO;
    navigationItem.searchController = nil;
}

// focus: true opens the search with the keyboard once the screen is shown, false hides the keyboard.
- (void)setFocused:(BOOL)focused {
    UISearchController *search = _viewController.navigationItem.searchController;
    if (!focused) {
        _focusWhenShown = NO;
        [search.searchBar resignFirstResponder];
        return;
    }
    if (!_visible || !search) {
        _focusWhenShown = YES;
        return;
    }
    _focusWhenShown = NO;
    dispatch_async(dispatch_get_main_queue(), ^{
      search.active = YES;
      [search.searchBar becomeFirstResponder];
    });
}

#pragma mark - The scope bar

// The selected segment is the one with the id of `selectedScope`, or the first. Before iOS 16 UIKit shows the scope bar
// while searching, whatever `scopeBarActivation` says.
- (void)applyScopes:(ANNSearchBarOptions *)options search:(UISearchController *)search {
    NSMutableArray<NSString *> *ids = [NSMutableArray new];
    NSMutableArray<NSString *> *titles = [NSMutableArray new];
    for (ANNSearchScopeOptions *scope in options.scopes) {
        [ids addObject:[scope.identifier withDefault:@""]];
        [titles addObject:[scope.title withDefault:@""]];
    }
    _scopeIds = ids;
    UISearchBar *searchBar = search.searchBar;
    if (!titles.count) {
        if (searchBar.scopeButtonTitles.count) {
            [self removeScopeBar:search];
        }
        return;
    }
    // UIKit shows the scope bar when the search opens or the user types: scopes added to an open search show the next
    // time it opens.
    if (![searchBar.scopeButtonTitles isEqualToArray:titles]) {
        searchBar.scopeButtonTitles = titles;
    }
    NSUInteger index = [ids indexOfObject:[options.selectedScope withDefault:@""]];
    searchBar.selectedScopeButtonIndex = index == NSNotFound ? 0 : (NSInteger)index;
    if (@available(iOS 16.0, *)) {
        search.scopeBarActivation = ANNScopeBarActivation([options.scopeBarActivation withDefault:@"automatic"]);
    }
}

// UIKit keeps an empty scope bar in an open search unless the scope bar is hidden by hand and the search bar and the
// navigation bar lay out again.
- (void)removeScopeBar:(UISearchController *)search {
    if (@available(iOS 16.0, *)) {
        search.scopeBarActivation = UISearchControllerScopeBarActivationManual;
    }
    UISearchBar *searchBar = search.searchBar;
    searchBar.scopeButtonTitles = nil;
    searchBar.showsScopeBar = NO;
    [searchBar sizeToFit];
    UINavigationBar *navigationBar = _viewController.navigationController.navigationBar;
    [navigationBar setNeedsLayout];
    [navigationBar layoutIfNeeded];
}

- (NSString *)selectedScope:(UISearchBar *)searchBar {
    NSInteger index = searchBar.selectedScopeButtonIndex;
    return index >= 0 && index < (NSInteger)_scopeIds.count ? _scopeIds[index] : nil;
}

#pragma mark - Results

- (UIViewController *)resultsController:(ANNSearchBarOptions *)options {
    ANNComponentOptions *component = options.resultsComponent;
    _resultsKey = ANNResultsKey(component);
    _resultsComponentId = _resultsKey ? component.componentId.get : nil;
    if (!_resultsKey) {
        return nil;
    }
    ANNReactView *reactView =
        [_componentRegistry createComponentIfNotExists:component
                                     parentComponentId:_viewController.layoutInfo.componentId
                                         componentType:ANNComponentTypeComponent
                                   reactViewReadyBlock:nil];
    return [[ANNSearchResultsViewController alloc] initWithReactView:reactView
                                                          componentId:_resultsComponentId
                                                         eventEmitter:_viewController.eventEmitter];
}

// UIKit shows the results controller while the field has text; showsSearchResultsController shows it at once and stops
// UIKit from hiding it when the field is empty, until the search closes.
- (void)applyResultsActivation:(ANNSearchBarOptions *)options search:(UISearchController *)search {
    _resultsOnSearchActivation =
        ![[options.resultsActivation withDefault:@"onSearchActivation"] isEqualToString:@"onTextEntry"];
    if (!search.searchResultsController) {
        return;
    }
    if (!_resultsOnSearchActivation) {
        search.automaticallyShowsSearchResultsController = YES;
    } else if (search.isActive) {
        search.showsSearchResultsController = YES;
    }
}

// The search events go to the screen and to its results component.
- (NSArray<NSString *> *)searchEventComponentIds {
    NSString *componentId = _viewController.layoutInfo.componentId;
    if (!componentId) {
        return @[];
    }
    return _resultsComponentId ? @[ componentId, _resultsComponentId ] : @[ componentId ];
}

#pragma mark - Tokens

static NSString *ANNTokenKey(ANNSearchTokenOptions *token) {
    return [NSString stringWithFormat:@"%@\n%@\n%@\n%@", [token.identifier withDefault:@""], [token.text withDefault:@""],
                                      [token.sfSymbol withDefault:@""], [token.icon withDefault:nil] ?: @""];
}

static NSArray<NSString *> *ANNTokenIds(NSArray<UISearchToken *> *tokens) {
    NSMutableArray<NSString *> *ids = [NSMutableArray new];
    for (UISearchToken *token in tokens) {
        if ([token.representedObject isKindOfClass:NSString.class]) {
            [ids addObject:token.representedObject];
        }
    }
    return ids;
}

// The field gets the tokens again only when the options changed: setting them resets the user's selection.
- (void)applyTokens:(NSArray<ANNSearchTokenOptions *> *)tokens field:(UISearchTextField *)field {
    if (!tokens) {
        return;
    }
    if (!_tokenOptions) {
        _tokenOptions = [NSMutableDictionary new];
    }
    NSMutableArray<NSString *> *keys = [NSMutableArray new];
    NSMutableArray<NSString *> *ids = [NSMutableArray new];
    for (ANNSearchTokenOptions *token in tokens) {
        NSString *tokenId = [token.identifier withDefault:@""];
        _tokenOptions[tokenId] = token;
        [keys addObject:ANNTokenKey(token)];
        [ids addObject:tokenId];
    }
    if ([_appliedTokens isEqualToArray:keys] && [ANNTokenIds(field.tokens) isEqualToArray:ids]) {
        return;
    }
    _appliedTokens = keys;
    NSMutableArray<UISearchToken *> *searchTokens = [NSMutableArray new];
    for (ANNSearchTokenOptions *token in tokens) {
        UIImage *icon = token.sfSymbol.hasValue ? [UIImage systemImageNamed:token.sfSymbol.get]
                                                : [token.icon withDefault:nil];
        UISearchToken *searchToken = [UISearchToken tokenWithIcon:icon text:[token.text withDefault:@""]];
        searchToken.representedObject = [token.identifier withDefault:@""];
        [searchTokens addObject:searchToken];
    }
    field.tokens = searchTokens;
}

// The tokens the field has now, written to the screen's options when the user deleted some (or brought them back with
// Undo), so the options apply them as they are. Their ids, or nil when the search bar has no tokens option.
- (NSArray<NSString *> *)syncTokens:(UISearchTextField *)field {
    if (!_tokenOptions) {
        return nil;
    }
    NSArray<NSString *> *ids = ANNTokenIds(field.tokens);
    NSMutableArray<ANNSearchTokenOptions *> *tokens = [NSMutableArray new];
    NSMutableArray<NSString *> *keys = [NSMutableArray new];
    for (NSString *tokenId in ids) {
        ANNSearchTokenOptions *token = _tokenOptions[tokenId];
        if (token) {
            [tokens addObject:token];
            [keys addObject:ANNTokenKey(token)];
        }
    }
    if (![_appliedTokens isEqualToArray:keys]) {
        _appliedTokens = keys;
        [_viewController changeOwnOptions:^(ANNNavigationOptions *options) {
          // Copies: the objects above belong to the applied tree, and two trees never share a group.
          NSMutableArray<ANNSearchTokenOptions *> *own = [NSMutableArray arrayWithCapacity:tokens.count];
          for (ANNSearchTokenOptions *token in tokens) {
              [own addObject:[token copy]];
          }
          options.topBar.searchBar.tokens = own;
        }];
    }
    return ids;
}

#pragma mark - Suggestions

static NSString *ANNSuggestionKey(ANNSearchSuggestionOptions *suggestion) {
    return [NSString stringWithFormat:@"%@\n%@\n%@\n%@\n%@", [suggestion.identifier withDefault:@""],
                                      [suggestion.title withDefault:@""], [suggestion.accessibilityLabel withDefault:@""],
                                      [suggestion.sfSymbol withDefault:@""], [suggestion.icon withDefault:nil] ?: @""];
}

- (void)applySuggestions:(NSArray<ANNSearchSuggestionOptions *> *)suggestions
                  search:(UISearchController *)search API_AVAILABLE(ios(16.0)) {
    if (!suggestions) {
        return;
    }
    NSMutableArray<NSString *> *keys = [NSMutableArray new];
    for (ANNSearchSuggestionOptions *suggestion in suggestions) {
        [keys addObject:ANNSuggestionKey(suggestion)];
    }
    if ([_appliedSuggestions isEqualToArray:keys] && search.searchSuggestions.count == keys.count) {
        return;
    }
    _appliedSuggestions = keys;
    NSMutableArray<UISearchSuggestionItem *> *items = [NSMutableArray new];
    for (ANNSearchSuggestionOptions *suggestion in suggestions) {
        UIImage *icon = suggestion.sfSymbol.hasValue ? [UIImage systemImageNamed:suggestion.sfSymbol.get]
                                                     : [suggestion.icon withDefault:nil];
        UISearchSuggestionItem *item =
            [UISearchSuggestionItem suggestionWithLocalizedSuggestion:[suggestion.title withDefault:@""]
                                                    descriptionString:[suggestion.accessibilityLabel withDefault:nil]
                                                            iconImage:icon];
        item.representedObject = [suggestion.identifier withDefault:@""];
        [items addObject:item];
    }
    search.searchSuggestions = items.count ? items : nil;
}

// UIKit has removed the suggestions: so do the screen's options, and the next ones from mergeOptions show.
- (void)syncSuggestions:(UISearchController *)search API_AVAILABLE(ios(16.0)) {
    if (_appliedSuggestions.count && !search.searchSuggestions.count) {
        _appliedSuggestions = @[];
        [_viewController changeOwnOptions:^(ANNNavigationOptions *options) {
          options.topBar.searchBar.suggestions = @[];
        }];
    }
}

#pragma mark - The search field color

// null removes the option.
- (void)applyFieldColor:(Color *)color field:(UISearchTextField *)field {
    if (!color.hasValue) {
        return;
    }
    if (!color.get) {
        _fieldColor = nil;
        if (_observedField) {
            UISearchTextField *observedField = _observedField;
            [self stopObservingField];
            observedField.backgroundColor = _systemFieldColor;
        }
        return;
    }
    _fieldColor = color.get;
    if (field != _observedField) {
        [self stopObservingField];
        _systemFieldColor = field.backgroundColor;
        _observedField = field;
        [field addObserver:self forKeyPath:@"backgroundColor" options:0 context:ANNSearchFieldColorContext];
    }
    [self setFieldColor:_fieldColor];
}

- (void)stopObservingField {
    [_observedField removeObserver:self forKeyPath:@"backgroundColor" context:ANNSearchFieldColorContext];
    _observedField = nil;
}

- (void)setFieldColor:(UIColor *)color {
    _settingFieldColor = YES;
    _observedField.backgroundColor = color;
    _settingFieldColor = NO;
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary<NSKeyValueChangeKey, id> *)change
                       context:(void *)context {
    if (context != ANNSearchFieldColorContext) {
        [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
        return;
    }
    if (_settingFieldColor || !_fieldColor) {
        return;
    }
    _systemFieldColor = _observedField.backgroundColor;
    [self setFieldColor:_fieldColor];
}

#pragma mark - UISearchResultsUpdating, UISearchBarDelegate, UISearchControllerDelegate

- (void)willPresentSearchController:(UISearchController *)searchController {
    if (_resultsOnSearchActivation && searchController.searchResultsController) {
        searchController.showsSearchResultsController = YES;
    }
}

// A placement asked for while the search was open is made on the next turn, once UIKit finished closing it.
- (void)didDismissSearchController:(UISearchController *)searchController {
    if (@available(iOS 26.0, *)) {
        if (!_placeWhenDismissed) {
            return;
        }
        __weak ANNSearchPresenter *weakSelf = self;
        dispatch_async(dispatch_get_main_queue(), ^{
          ANNSearchPresenter *presenter = weakSelf;
          if (presenter && presenter->_placeWhenDismissed) {
              [presenter placeSearchBarAgain];
          }
        });
    }
}

- (void)updateSearchResultsForSearchController:(UISearchController *)searchController {
    if (@available(iOS 16.0, *)) {
        [self syncSuggestions:searchController];
    }
    UISearchBar *searchBar = searchController.searchBar;
    NSArray<NSString *> *tokens = [self syncTokens:searchBar.searchTextField];
    NSString *scope = [self selectedScope:searchBar];
    ANNEventEmitter *eventEmitter = _viewController.eventEmitter;
    for (NSString *componentId in self.searchEventComponentIds) {
        [eventEmitter sendOnSearchBarUpdated:componentId
                                        text:searchBar.text ?: @""
                                   isFocused:searchBar.isFirstResponder
                                       scope:scope
                                      tokens:tokens];
    }
}

// A picked suggestion sends searchSuggestionPicked with its id; UIKit removes the suggestions and keeps the text.
- (void)updateSearchResultsForSearchController:(UISearchController *)searchController
                     selectingSearchSuggestion:(id<UISearchSuggestion>)searchSuggestion API_AVAILABLE(ios(16.0)) {
    [self syncSuggestions:searchController];
    id suggestionId = searchSuggestion.representedObject;
    if ([suggestionId isKindOfClass:NSString.class]) {
        UIViewController<ANNLayoutProtocol> *viewController = _viewController;
        [viewController.eventEmitter sendOnSearchSuggestionPicked:viewController.layoutInfo.componentId
                                                     suggestionId:suggestionId];
    }
}

// The pick is written to the screen's options, so it stays when the options apply again. UIKit then updates the
// search results: searchBarUpdated carries the new scope.
- (void)searchBar:(UISearchBar *)searchBar selectedScopeButtonIndexDidChange:(NSInteger)selectedScope {
    NSString *scope = [self selectedScope:searchBar];
    if (scope) {
        [_viewController changeOwnOptions:^(ANNNavigationOptions *options) {
          options.topBar.searchBar.selectedScope = [Text withValue:scope];
        }];
    }
}

// The Search key: UIKit hides the keyboard and keeps the text and the search open.
- (void)searchBarSearchButtonClicked:(UISearchBar *)searchBar {
    NSArray<NSString *> *tokens = [self syncTokens:searchBar.searchTextField];
    NSString *scope = [self selectedScope:searchBar];
    ANNEventEmitter *eventEmitter = _viewController.eventEmitter;
    for (NSString *componentId in self.searchEventComponentIds) {
        [eventEmitter sendOnSearchBarSubmitted:componentId text:searchBar.text ?: @"" scope:scope tokens:tokens];
    }
}

- (void)searchBarCancelButtonClicked:(UISearchBar *)searchBar {
    ANNEventEmitter *eventEmitter = _viewController.eventEmitter;
    for (NSString *componentId in self.searchEventComponentIds) {
        [eventEmitter sendOnSearchBarCancelPressed:componentId];
    }
}

@end
