#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Whether the app can show pairs at all. The state of each screen of a pair (`useArrangement`) is kept for JavaScript
/// by `ANNScreenEnvironment`.
@interface ANNArrangementState : NSObject

/// Whether the app shows arrangements (`getStartInfo().arrangements`): built with the iOS 27.1 SDK and running on
/// iOS 27.1 or later. Without them JavaScript sends only the primary, as a screen. Any thread.
+ (BOOL)canShowArrangements;

@end

NS_ASSUME_NONNULL_END
