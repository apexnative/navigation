#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// What `takeLaunchLink:` answers with: the link that launched the app, or nil.
typedef void (^ANNLaunchLinkCompletion)(NSURL *_Nullable url);

/**
 * The deep links on their way to React Native's `Linking`, and the link that launched the app. Main queue only.
 *
 * A link that arrives before React content first appears waits here, then goes to `Linking` as a `url` event; later
 * ones go at once. The launch links are known once the app's scene connected (without the scene life cycle: once the
 * app became active); the latest one queued by then is the link that launched the app.
 */
@interface ANNDeepLinkQueue : NSObject

/// Starts the queue as the app launches (`ANNAppDelegate`). An app that never starts it has no launch link.
+ (void)start;

/// Hands `url` to `Linking`, or keeps it until React content first appears and no `takeLaunchLink:` waits.
+ (void)dispatchURL:(NSURL *)url;

/// The app's window scene connected (`+[ApexNavigation attachToWindowScene:]`).
+ (void)windowSceneDidConnect;

/**
 * Gives `completion` the link that launched the app, once the launch links are known: the latest link queued. It
 * leaves the queue, and so do the links before it, so `Linking` never gets them. Given once: later calls get nil.
 * An app launched in the background gets nil at once, and its queue goes to `Linking` as usual.
 */
+ (void)takeLaunchLink:(ANNLaunchLinkCompletion)completion;

@end

NS_ASSUME_NONNULL_END
