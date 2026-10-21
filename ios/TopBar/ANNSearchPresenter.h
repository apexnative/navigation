#import "ANNReactComponentRegistry.h"
#import "ANNSearchBarOptions.h"
#import "UIViewController+LayoutProtocol.h"
#import <UIKit/UIKit.h>

// topBar.searchBar: the UISearchController in the screen's navigation item, and the search events of the screen.
@interface ANNSearchPresenter : NSObject

// The registry creates the React view of `resultsComponent`.
- (instancetype)initWithComponentRegistry:(ANNReactComponentRegistry *)componentRegistry;

- (void)bindViewController:(UIViewController<ANNLayoutProtocol> *)viewController;

// The screen is about to appear: the search bar as the options describe it.
- (void)applyOptions:(ANNSearchBarOptions *)options;

// mergeOptions: the search bar from the resolved options when the merge changes any of them. Focus only when the merge
// sets it.
- (void)mergeOptions:(ANNSearchBarOptions *)options resolvedOptions:(ANNSearchBarOptions *)resolvedOptions;

// The search field went into the screen's toolbar items or out of them (`toolbar.buttons` with `searchField`): the
// search controller is set again, so UIKit places its bar again; an open search waits until it closes.
- (void)placeSearchBarAgain API_AVAILABLE(ios(26.0));

- (void)componentDidAppear;

- (void)componentDidDisappear;

@end
