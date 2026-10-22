#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Scene delegate for apps on the UIScene life cycle, which apps built with the iOS 27 SDK must use.
 *
 * `ANNAppDelegate` assigns it to the app's scene unless the Info.plist names a scene delegate of its own.
 * When the scene connects, the navigation window moves into it. URLs, user activities and foreground/background
 * changes that UIKit now delivers to the scene are forwarded to the app delegate's usual methods, so
 * the deep links (`dispatchDeepLinkURL:`) and app delegate overrides keep working.
 *
 * The app's own scene delegate can extend it (`class SceneDelegate: ANNSceneDelegate {}`, which `setup` makes of React
 * Native 0.88's template); a method it overrides calls `super`.
 */
@interface ANNSceneDelegate : UIResponder <UIWindowSceneDelegate>

@property(nonatomic, strong, nullable) UIWindow *window;

@end

NS_ASSUME_NONNULL_END
