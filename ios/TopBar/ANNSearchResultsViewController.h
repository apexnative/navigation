#import "ANNEventEmitter.h"
#import "ANNReactView.h"
#import <UIKit/UIKit.h>

// The results screen of `searchBar.resultsComponent`: UIKit shows it over the screen while the search is open. The
// component covers all of it, under the search bar and to the bottom edge, on the system background; its safe area
// gives the room the bar and the bottom take.
@interface ANNSearchResultsViewController : UIViewController

- (instancetype)initWithReactView:(ANNReactView *)reactView
                      componentId:(NSString *)componentId
                     eventEmitter:(ANNEventEmitter *)eventEmitter;

@property(nonatomic, readonly) ANNReactView *reactView;

@end
