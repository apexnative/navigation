#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The main list of a screen: the first visible `UIScrollView` in depth (at most 16 levels; hidden views and what is in
/// them skipped; not a `UITextView`) whose top is within 1 pt of the screen view's top and whose width is at least the
/// screen's width minus 1 pt. Kept while React keeps it in the screen, searched again only when React took it out. Only
/// read: nothing in React Native's views changes.
@interface ANNMainList : NSObject

- (instancetype)initWithScreenView:(UIView *)screenView;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The main list now: the one kept while it is still in the screen's view, else a new search.
- (nullable UIScrollView *)list;

/// The list kept, without a search; nil when none or React took it out.
- (nullable UIScrollView *)keptList;

/// A view the core lays into a screen's view for its own needs (the place of a large title during a transition): no
/// search takes it, or what is in it, for the screen's list.
+ (void)excludeView:(UIView *)view;

/// The view is one of those (`excludeView:`).
+ (BOOL)isExcludedView:(UIView *)view;

@end

NS_ASSUME_NONNULL_END
