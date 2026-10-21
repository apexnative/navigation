#import <ApexNavigationSpec/ApexNavigationSpec.h>

NS_ASSUME_NONNULL_BEGIN

/// The commands and questions of JavaScript (`NativeANNTurboModule`). A command with a promise works on the main queue
/// and answers once it is done; the synchronous methods answer at once on the JavaScript thread from what the main
/// queue stored.
@interface ANNTurboModule : NSObject <NativeANNTurboModuleSpec>
@end

NS_ASSUME_NONNULL_END
