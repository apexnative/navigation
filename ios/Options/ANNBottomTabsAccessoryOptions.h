#import "ANNComponentOptions.h"
#import "ANNOptions.h"

/**
 * `bottomTabs.bottomAccessory` (iOS 26): a React component above the tab bar, and next to it when
 * the tab bar is minimized.
 */
@interface ANNBottomTabsAccessoryOptions : ANNOptions

@property(nonatomic, strong) ANNComponentOptions *component;

/** Shows or hides the accessory; shown by default when it has a component. */
@property(nonatomic, strong) Bool *visible;

@property(nonatomic, strong) Text *testID;

- (BOOL)hasValue;

@end
