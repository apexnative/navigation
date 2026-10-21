#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Where the React views of unselected tabs mount while the tabs render: a window of zero size that clips what it
/// holds, hidden from accessibility, in the scene of the navigation's main window. Nothing in it is seen and its safe
/// area views never lay out there: a tab renders its content where it shows. One per tabs node; it goes when it holds
/// nothing more or with its tabs. Main queue.
@interface ANNTabsPreloadWindow : UIWindow

/// A new window in the scene of the main window; when the main window has no scene yet, it joins the scene as the main
/// window connects (`mainWindowSceneDidConnect:`), before it holds anything that could be seen.
+ (instancetype)preloadWindow;

/// The main window connected to its scene: every live preload window joins it.
+ (void)mainWindowSceneDidConnect:(UIWindowScene *)windowScene;

/// A reload of the JavaScript: every live preload window hides at once; its views go with their old tabs after the
/// reload's hold.
+ (void)hideAllAfterReload;

/// The window goes: it hides and leaves its scene. Its views are the tabs' to take back first.
- (void)close;

@end

NS_ASSUME_NONNULL_END
