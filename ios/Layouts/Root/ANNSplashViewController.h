#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What the navigation's window shows while it has no root of the app: the copy of the launch screen at the start, the
/// blank screen after a reload of JavaScript. Both count as the splash: a root without an animation of its own replaces
/// either at once. The status bar follows the Info.plist keys `UIStatusBarStyle` and `UIStatusBarHidden`. Main queue.
@interface ANNSplashViewController : UIViewController

/// The copy of the launch screen. When iOS builds it from the Info.plist (`UILaunchScreen`, or the default of
/// `UILaunchScreens`), the same color, image and bars as plain views; else the initial view controller of
/// `UILaunchStoryboardName` as a child (a storyboard that doesn't load: the first view of the nib of that name), else
/// the legacy launch images, full screen. Nil when the app has none of these: the window then has no root view
/// controller until the first root.
+ (nullable instancetype)launchScreenCopyWithBounds:(CGRect)bounds;

/// The blank screen after a reload: clear when the default options give the window a color (`window.backgroundColor`),
/// else the system background.
+ (instancetype)blankScreen;

/// Whether this is the blank screen of a reload.
@property(nonatomic, readonly) BOOL isBlankScreen;

- (instancetype)initWithNibName:(nullable NSString *)nibNameOrNil
                         bundle:(nullable NSBundle *)nibBundleOrNil NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;
- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
