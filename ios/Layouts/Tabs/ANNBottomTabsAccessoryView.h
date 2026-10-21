#import "ANNEventEmitter.h"
#import "ANNReactView.h"
#import <UIKit/UIKit.h>

/**
 * The content of the tab bar's bottom accessory (iOS 26): the React component fills it, and the
 * component gets `bottomAccessoryPlacementChanged` when UIKit moves the accessory between the
 * place above the tab bar ('regular') and the place next to the minimized tab bar ('inline').
 */
@interface ANNBottomTabsAccessoryView : UIView

- (instancetype)initWithReactView:(ANNReactView *)reactView
                     eventEmitter:(ANNEventEmitter *)eventEmitter;

@property(nonatomic, strong, readonly) ANNReactView *reactView;

@end
