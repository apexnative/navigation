#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The direction of the app: `layout.direction` of the default options, which JS puts there (`createNavigation({
/// direction })`, `setDirection`), `'ltr'`, `'rtl'` or `'locale'`.
///
/// Applied at every `setRoot` (step 1) and every `setDefaultOptions` whose defaults have the key, and only when it
/// differs from the value applied last: React Native's direction at once; on iOS 17+ the trait of the navigation
/// window's scene, its windows take it at once, then the shown stacks drop the direction their pushes forced (kept for
/// later when the window has no scene yet); on iOS 15–16 the appearance of the views made after it. Without the key
/// nothing changes. Main queue.
@interface ANNAppDirection : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `layout.direction` of the default options now, applied as described above.
+ (void)applyDefaultOptionsDirection;

/// The navigation window joined its scene: the scene's part of a direction set before applies now.
+ (void)windowSceneDidConnect;

/// What a push forces on its stack's view and bar before UIKit's change: React Native's direction, without the key or
/// before iOS 17; with the key on iOS 17+ nothing, so that the stack follows the scene.
@property(class, nonatomic, readonly) UISemanticContentAttribute pushDirection;

/// React Native lays out from right to left (`I18nManager`): its scroll views are mirrored then, whatever UIKit's
/// direction.
@property(class, nonatomic, readonly) BOOL reactNativeIsRightToLeft;

/// `navigation` (a shown stack, UIKit's More navigation) follows the scene's direction now: its view and bar get the
/// direction their traits have, then none, so that UIKit takes the swipe's edge again even when they had none already
/// (a stack that followed the scene through one change keeps the old edge otherwise).
+ (void)followSceneDirectionIn:(UINavigationController *)navigation;

/// `navigation`, in a window now, didn't follow the scene's last change of direction from a window (it was off screen
/// then, where its traits stayed the old ones): it follows when it shows.
+ (BOOL)navigationMissedSceneDirection:(UINavigationController *)navigation;

@end

NS_ASSUME_NONNULL_END
