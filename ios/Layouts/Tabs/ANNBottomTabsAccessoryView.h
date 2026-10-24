#import "ANNEventEmitter.h"
#import "ANNReactView.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * The content of the tab bar's bottom accessory (iOS 26): the React component fills it, and the
 * component gets `bottomAccessoryPlacementChanged` when UIKit moves the accessory between the
 * place above the tab bar ('regular') and the place next to the minimized tab bar ('inline').
 */
@interface ANNBottomTabsAccessoryView : UIView

- (instancetype)initWithReactView:(ANNReactView *)reactView
                     eventEmitter:(nullable ANNEventEmitter *)eventEmitter;

@property(nonatomic, strong, readonly) ANNReactView *reactView;

/**
 * `handler` runs once when the view is out of a window: at once when it is already, else when it
 * leaves it, so a replaced accessory shows to the end of whatever UIKit draws of it. nil cancels a
 * handler not run yet.
 */
- (void)whenOutOfWindow:(nullable dispatch_block_t)handler;

@end

NS_ASSUME_NONNULL_END
