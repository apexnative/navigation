#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The life of the JavaScript the navigation answers to.
///
/// The generation is a number that grows each time the JavaScript goes: when React Native's reload command is given,
/// and again when the next bundle loaded (the only signal of every reload). Every command takes it when it arrives; its queued work and its answer do nothing once the number
/// moved: the commands of the JavaScript that went get no answer, and its screens tell the new JavaScript nothing.
///
/// React Native tells when a bundle loaded, the first one of the process too. After a reload the navigation of the old
/// JavaScript goes; after every bundle the navigation state is forgotten and `ANN.AppLaunched` goes to JavaScript.
@interface ANNReloadCoordinator : NSObject

/// The generation of the JavaScript that runs now. Any thread.
@property(class, nonatomic, readonly) NSUInteger generation;

/// How many bundles ran in the process so far, counted as each one's `AppLaunched` goes. Any thread.
@property(class, nonatomic, readonly) NSUInteger bundleCount;

/// Starts watching the bundles: once, when the navigation starts (`bootstrapWithHost:`). Main queue.
+ (void)start;

@end

NS_ASSUME_NONNULL_END
