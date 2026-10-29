#import <UIKit/UIKit.h>

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

/// Starts the queue as the app launches (`+[ApexNavigation bootstrapWithHost:]`, so also under an app delegate of the
/// app's own). Once: a second call does nothing.
+ (void)start;

/// Hands `url` to `Linking`, or keeps it until React content first appears and no `takeLaunchLink:` waits.
+ (void)dispatchURL:(NSURL *)url;

/**
 * Runs `forward`, which hands the scene's link to the app delegate (`ANNSceneDelegate`). The URL the app delegate
 * dispatches in it keeps the scene and its context, which React Native 0.88 and newer need to take a scene's link.
 */
+ (void)forwardURLContext:(UIOpenURLContext *)context
                  ofScene:(UIScene *)scene
                  through:(NS_NOESCAPE dispatch_block_t)forward;

/// `forwardURLContext:ofScene:through:` for a web link (`webpageURL`) of the scene.
+ (void)forwardUserActivity:(NSUserActivity *)activity
                    ofScene:(UIScene *)scene
                    through:(NS_NOESCAPE dispatch_block_t)forward;

/// `dispatchURL:` for the scene's link, with its scene and context: for an app delegate that doesn't take links.
+ (void)dispatchURLContext:(UIOpenURLContext *)context ofScene:(UIScene *)scene;

/// `dispatchURLContext:ofScene:` for a web link (`webpageURL`) of the scene; an activity without one does nothing.
+ (void)dispatchUserActivity:(NSUserActivity *)activity ofScene:(UIScene *)scene;

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
