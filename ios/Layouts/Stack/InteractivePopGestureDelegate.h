#import <UIKit/UIKit.h>

@class ANNStackController;

NS_ASSUME_NONNULL_BEGIN

/// The delegate of one of a stack's swipes back: the edge swipe (`interactivePopGestureRecognizer`) or, on iOS 26, the
/// swipe from the content (`interactiveContentPopGestureRecognizer`). It keeps UIKit's own delegate of the recognizer
/// and hands it every question it doesn't answer itself, so the links UIKit makes between the swipe and the gestures of
/// the content stay (a horizontal list scrolls and bounces at its start). The side menu reads `enabled` of the content
/// swipe's delegate and asks the edge swipe's delegate whether it takes a touch.
@interface InteractivePopGestureDelegate : NSObject <UIGestureRecognizerDelegate>

/// A delegate of no stack: it answers only `enabled`.
- (instancetype)init;

/// The delegate of a swipe of `stack`, over UIKit's own delegate of the recognizer. `content` is the swipe from the
/// content.
- (instancetype)initWithStack:(nullable ANNStackController *)stack
               systemDelegate:(nullable id<UIGestureRecognizerDelegate>)systemDelegate
                      content:(BOOL)content NS_DESIGNATED_INITIALIZER;

/// `popGesture` of the screen on top lets this swipe go back: the edge swipe unless it is `false`, the swipe from
/// the content only when it is `true` (`popGesture`). The stack sets it at each appearance of a top screen and at each
/// `mergeOptions` of it.
@property(nonatomic) BOOL enabled;

/// UIKit's own delegate of the recognizer.
@property(nonatomic, strong, readonly, nullable) id<UIGestureRecognizerDelegate> systemDelegate;

/// The bar of the navigation that shows the stack's screens is hidden. UIKit's delegate refuses every swipe back
/// then, in questions of its own that come before the public ones; while the bar is hidden the swipe passes UIKit's
/// delegate only the public questions of `UIGestureRecognizerDelegate`, and the core decides. A change sets the
/// delegate on the recognizer again (UIKit reads which questions a delegate answers when it is set). The stack sets
/// it as the bar shows or hides.
@property(nonatomic) BOOL barHidden;

/// The one way a swipe back gets the delegate of `stack`: on the stack's own navigation and on UIKit's More navigation
/// while it shows the stack's screens. UIKit's own delegate stays under it, also when a delegate of another stack
/// was there. `barSwipe` (the navigation's `barHideOnSwipeGestureRecognizer`) waits for the swipe to fail, once, so that
/// a horizontal swipe still goes back with `hidesBarsOnSwipe`. Returns the delegate on the
/// recognizer, the one there when it is already this stack's.
+ (nullable InteractivePopGestureDelegate *)installOn:(nullable UIGestureRecognizer *)recognizer
                                                stack:(ANNStackController *)stack
                                              content:(BOOL)content
                                             barSwipe:(nullable UIGestureRecognizer *)barSwipe;

/// The recognizer gets UIKit's own delegate back when a delegate of a stack is on it (the More list shows again).
+ (void)restoreSystemDelegateOf:(nullable UIGestureRecognizer *)recognizer;

@end

NS_ASSUME_NONNULL_END
