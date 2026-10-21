#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The repeat press on a tab whose stack is on its first screen: the screen's content goes to its top, animated. It
/// reads the views only and moves only the scroll offset: nothing of React Native's layout changes.
@interface ANNScrollToTop : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The first `UIScrollView` under `view` (depth first, shown ones only) that scrolls more than horizontally goes to its
/// top with its content inset, animated. With a large title that can show in `navigationBar` (shown), "the top" is
/// where the title is expanded, as at the screen's first show: the bar measures its insets collapsed while the list is
/// scrolled, so the list goes past that top by the large title's row and UIKit expands the title as it tracks the
/// list. NO when there is no such scroll view.
+ (BOOL)scrollToTopIn:(nullable UIView *)view
        navigationBar:(nullable UINavigationBar *)navigationBar
      largeTitleShows:(BOOL)largeTitleShows;

@end

NS_ASSUME_NONNULL_END
