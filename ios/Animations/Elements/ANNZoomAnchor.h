#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The screens the source view of a zoom is looked for in, asked each time UIKit wants the source.
typedef NSArray<UIViewController *> *_Nonnull (^ANNZoomSourceScreens)(void);

/// The iOS 18 zoom of a push or a modal. UIKit gets as its source not the React view but an anchor: an empty view of
/// the core in the source screen's own view, outside React's hierarchy, at the React view's frame with its corner
/// radius and corner curve, taking no touches (UIKit moves its source in its parent during the zoom, which a React view
/// must never undergo). One anchor per screen (a new one replaces the old); it goes at the end of each transition it
/// served. The source screens are held weakly; the view is looked up again each time UIKit asks (also at the pop or the
/// dismissal), and without it UIKit zooms to the center. Main queue.
@interface ANNZoomAnchor : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `zoomed.preferredTransition` becomes the zoom from the view with `fromId`, when that view is found on one of the
/// screens and is in a window now. YES when the zoom is set.
+ (BOOL)zoom:(UIViewController *)zoomed
          fromId:(NSString *)fromId
    sourceScreens:(ANNZoomSourceScreens)sourceScreens API_AVAILABLE(ios(18.0));

/// A transition `zoomed` took part in ended: the anchor its zoom placed goes (the next ask places a new one).
+ (void)transitionEndedOf:(nullable UIViewController *)zoomed;

/// A transition of `stack` ended: the anchors on its screens go (the zoomed screen may have left the stack by now).
+ (void)transitionEndedInStack:(UINavigationController *)stack;

@end

NS_ASSUME_NONNULL_END
