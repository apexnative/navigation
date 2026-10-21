#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// A React component in the options (`component`, `resultsComponent`): its name and the id JS gave it. The `id` the app
/// may give becomes `componentId` in JS, and `passProps` stay in JS by that id.
@interface ANNComponentOptions : ANNOptions

@property(nonatomic, strong) Text *name;
@property(nonatomic, strong) Text *componentId;

@end

NS_ASSUME_NONNULL_END
