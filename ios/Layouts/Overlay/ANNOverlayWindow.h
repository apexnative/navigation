#import <UIKit/UIKit.h>

@interface ANNOverlayWindow : UIWindow
@property(nonatomic, weak, nullable) UIWindow *previousWindow;
/// A field of the overlay took the keyboard: the window became key when the user touched it.
@property(nonatomic, readonly) BOOL takesKeyboard;
/// The overlay's `overlay.interceptTouchOutside`, read by the core at the show and after each merge: a touch no view of
/// the overlay takes stays in the overlay instead of reaching the windows under it.
@property(nonatomic) BOOL interceptsTouchOutside;
@end
