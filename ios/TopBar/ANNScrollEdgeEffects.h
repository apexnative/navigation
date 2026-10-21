#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The scroll edge effect of a screen's lists (iOS 26; `scrollEdgeEffect.*`): each edge's own `hidden` / `style` beat
/// the general ones, through the public API. No top effect under a solid `background.color` (not a clear one) or
/// `background.component` unless the screen set its own for the top or for all edges. The screen remembers the lists it
/// set and gives them the system values back (`hidden` NO, `style` automatic) before its content goes and when it
/// leaves, and sets them again at its appearance. Before iOS 26 nothing. Main queue.
@interface ANNScrollEdgeEffects : NSObject

- (instancetype)initWithScreenView:(UIView *)screenView;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// At the appearance and at a merge: every scroll view in the screen's view, set again whole.
- (void)applyToAllWithOptions:(ANNNavigationOptions *)options;

/// After a mount of the content: the main list only; lists React took out of the screen get the system values.
- (void)applyToList:(nullable UIScrollView *)list options:(ANNNavigationOptions *)options;

/// The lists set get the system values back (the screen leaves, its content goes, its destroy).
- (void)reset;

@end

NS_ASSUME_NONNULL_END
