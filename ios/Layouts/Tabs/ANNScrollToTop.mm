#import "ANNScrollToTop.h"

// UIKit's row of an expanded large title, and the height under which a bar shows no expanded large title (a bar with a
// large title is at least the row taller than its 44–54 pt collapsed height).
static const CGFloat ANNLargeTitleRowHeight = 52;
static const CGFloat ANNCollapsedBarLimit = 70;

// A scroll view that scrolls only horizontally has content no taller than itself and no vertical bounce.
static BOOL ANNScrollsVertically(UIScrollView *scrollView) {
    UIEdgeInsets inset = scrollView.adjustedContentInset;
    CGFloat contentHeight = scrollView.contentSize.height + inset.top + inset.bottom;
    CGFloat contentWidth = scrollView.contentSize.width + inset.left + inset.right;
    BOOL onlyHorizontal = contentWidth > scrollView.bounds.size.width &&
                          contentHeight <= scrollView.bounds.size.height &&
                          !scrollView.alwaysBounceVertical;
    return !onlyHorizontal;
}

static UIScrollView *_Nullable ANNFirstVerticalScrollView(UIView *view) {
    if (view.hidden || view.alpha <= 0) {
        return nil;
    }
    if ([view isKindOfClass:UIScrollView.class] && ANNScrollsVertically((UIScrollView *)view)) {
        return (UIScrollView *)view;
    }
    for (UIView *subview in view.subviews) {
        UIScrollView *found = ANNFirstVerticalScrollView(subview);
        if (found) {
            return found;
        }
    }
    return nil;
}

@implementation ANNScrollToTop

+ (BOOL)scrollToTopIn:(UIView *)view
        navigationBar:(UINavigationBar *)navigationBar
      largeTitleShows:(BOOL)largeTitleShows {
    UIScrollView *scrollView = view ? ANNFirstVerticalScrollView(view) : nil;
    if (!scrollView) {
        return NO;
    }
    CGFloat top = -scrollView.adjustedContentInset.top;
    // Scrolled under a large title that collapsed: past the collapsed top by the title's row.
    BOOL scrolled = scrollView.contentOffset.y > top + 0.5;
    if (largeTitleShows && scrolled && navigationBar.prefersLargeTitles &&
        navigationBar.frame.size.height < ANNCollapsedBarLimit) {
        top -= ANNLargeTitleRowHeight;
    }
    [scrollView setContentOffset:CGPointMake(scrollView.contentOffset.x, top) animated:YES];
    return YES;
}

@end
