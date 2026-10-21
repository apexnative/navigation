#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Small answers about the app's window and screen that the core and the modules share: the navigation window and its
/// scene take the place of the app-wide window APIs. Main queue.
@interface ANNUtils : NSObject

/// The window the navigation shows its root in; nil before the navigation started.
@property(class, nonatomic, readonly, nullable) UIWindow *mainWindow;

/// The screen of the navigation's window, or the device's main screen before the window has one.
@property(class, nonatomic, readonly) UIScreen *mainScreen;

/// The windows of the app: those of every connected window scene, and the navigation window when it isn't among them
/// yet. The searches of screens go through these.
@property(class, nonatomic, readonly) NSArray<UIWindow *> *windows;

/// The key window: the one of the navigation window's scene, else of the first other connected scene that has one,
/// else the navigation window.
@property(class, nonatomic, readonly, nullable) UIWindow *keyWindow;

/// The key window of `scene`; nil when none of its windows is key.
+ (nullable UIWindow *)keyWindowOfScene:(nullable UIWindowScene *)scene;

/// The status bar's frame in the navigation window's scene (its status bar manager); `CGRectZero` without a scene.
@property(class, nonatomic, readonly) CGRect statusBarFrame;

/// The orientations of the app for the navigation window (F4): from iOS 27 its scene's delegate when it answers them,
/// else the app delegate when it answers them, else the `Info.plist` of the app. Never empty.
@property(class, nonatomic, readonly) UIInterfaceOrientationMask appOrientationMask;

@end

NS_ASSUME_NONNULL_END
