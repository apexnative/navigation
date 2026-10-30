#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Posted on the main queue when the screens' geometry has settled: each screen sends its insets, padding and regions
/// once.
extern NSNotificationName const ANNScreenSettlingDidEndNotification;

/// iPhone Duo (iOS 27.1) folds and unfolds in steps: around the size transition, UIKit gives the views safe areas and a
/// vertical bar edge that are neither the old nor the new ones (the bar on the other side, insets on both sides,
/// twice its width) and tells of the bar's edge and of the safe area at other moments. While the geometry settles the
/// screens lay out as always but send nothing to JavaScript: the settling ends once the transition is over and the
/// values of the screens have stood still for a short while, and the screens send what they have then. Only on a
/// device with a hinge: elsewhere a rotation gives the new safe area at once, and it goes at once. Main queue.
@interface ANNScreenSettling : NSObject

/// A size transition starts: the settling runs at least until it ends.
+ (void)beginWithTransition:(id<UIViewControllerTransitionCoordinator>)coordinator;

/// The vertical bar's edge changed: the size transition, when one follows, comes a little later. The hinge's moves
/// begin the settling too (`ANNHingeDidMoveNotification`): UIKit may give a new safe area before either.
+ (void)beginForTraitChange;

/// Whether the screens hold their insets now.
+ (BOOL)isSettling;

/// A screen's values changed while settling: the quiet time starts again.
+ (void)valuesDidChange;

@end

NS_ASSUME_NONNULL_END
