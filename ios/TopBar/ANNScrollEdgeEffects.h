#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The scroll edge effect of a screen's lists (iOS 26; `scrollEdgeEffect.*`): each edge's own `hidden` / `style` beat
/// the general ones, through the public API. No top effect under a solid `background.color` (not a clear one) or
/// `background.component` unless the screen set its own for the top or for all edges. The lists are the scroll views
/// of the screen's view down to 16 levels, outside hidden views (`display: none`), not inside a list that scrolls
/// vertically (its rows) or a text view; a scroll view that doesn't (a pager, a carousel) is looked into for its
/// lists. The screen remembers the lists it set and gives them the system values back when it leaves, at its destroy
/// and when they leave its view for no screen (Fabric's pool; in another screen they may be that screen's already).
/// Before iOS 26 nothing. Main queue.
@interface ANNScrollEdgeEffects : NSObject

- (instancetype)initWithScreenView:(UIView *)screenView;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// At the appearance and at a merge: the lists of the screen's view, set again whole. Without a walk (only the known
/// lists still in the view set again) when the options it reads and the traits are the same as at the last walk and
/// no content mounted since, while the screen's content tells all its mounts (`mountsReported`) and that walk found
/// no pager.
- (void)applyToAllWithOptions:(ANNNavigationOptions *)options;

/// The screen's content tells each of its mounts, also out of the window (a React root): without it every call walks
/// the view.
@property(nonatomic) BOOL mountsReported;

/// After a mount of the content: the main list only; lists React took out of the screen get the system values, and
/// the next `applyToAllWithOptions:` walks the view again.
- (void)applyToList:(nullable UIScrollView *)list options:(ANNNavigationOptions *)options;

/// The screen left: its lists get the system values back (a list Fabric recycles from it comes clean), and stay
/// known for its next appearance.
- (void)screenDidDisappear;

/// The lists set get the system values back and are forgotten (the screen's content goes, its destroy); the next
/// `applyToAllWithOptions:` walks the view again.
- (void)reset;

@end

NS_ASSUME_NONNULL_END
