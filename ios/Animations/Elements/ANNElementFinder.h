#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Finds the React views of the elements of a transition by their `nativeID`: depth first, the first match, in the
/// React view of the screen a side of the transition shows — the visible screen of the side's node
/// (`+[ANNAppearanceDispatcher visibleScreenOf:]`: a modal's stack its top, tabs the selected one; a pair its primary).
/// Every transition of the core finds its elements here; nothing is changed. Main queue.
@interface ANNElementFinder : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The React view of the visible screen of `node`; nil without one (a native screen, no screen).
+ (nullable UIView *)searchRootOf:(nullable UIViewController *)node;

/// The view with `nativeID` in `root` and its subviews; nil when none has it or `nativeID` is empty.
+ (nullable UIView *)viewWithNativeID:(nullable NSString *)nativeID inView:(nullable UIView *)root;

/// The view with `nativeID` in the visible screen of `node`.
+ (nullable UIView *)viewWithNativeID:(nullable NSString *)nativeID
                               inNode:(nullable UIViewController *)node;

@end

NS_ASSUME_NONNULL_END
