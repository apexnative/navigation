#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What the animating delegate asks of its stack: the transition object for the operation UIKit starts.
@protocol ANNStackAnimating <UINavigationControllerDelegate>

/// The custom transition the stack's queue prepared for this operation; nil for any other (UIKit's own pops, the
/// split view's replace, nothing own, a zoom alone).
- (nullable id<UIViewControllerAnimatedTransitioning>)
    animatorForOperation:(UINavigationControllerOperation)operation
                    from:(UIViewController *)from
                      to:(UIViewController *)to;

@end

/// The stack's delegate while a custom animation may be needed: it answers what the stack answers as its own delegate —
/// every method the stack implements goes to the stack — and also the choice of the transition object. A delegate with
/// that method turns the iOS 26 content swipe off, so the stack sets this one only while the top's `animations.pop`,
/// the pushed screen's `animations.push` or a running `setStackRoot` is its own, and itself otherwise. Never an
/// interaction controller: the custom transitions aren't interactive, the swipe stays UIKit's.
@interface ANNStackAnimatingDelegate : NSObject <UINavigationControllerDelegate>

- (instancetype)initWithStack:(UINavigationController<ANNStackAnimating> *)stack
    NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
