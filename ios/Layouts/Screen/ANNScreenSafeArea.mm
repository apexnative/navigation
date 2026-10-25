#import "ANNScreenSafeArea.h"
#import "ANNScreenBars.h"

CGRect ANNScreenRootFrame(CGRect bounds, UIEdgeInsets safeArea, BOOL fromTop, BOOL toBottom) {
    CGFloat top = fromTop ? 0 : safeArea.top;
    CGFloat bottom = toBottom ? CGRectGetHeight(bounds) : CGRectGetHeight(bounds) - safeArea.bottom;
    return CGRectMake(0, top, CGRectGetWidth(bounds), MAX(0, bottom - top));
}

UIEdgeInsets ANNScreenInsetsOverRoot(CGRect rootFrame, CGSize viewSize, UIEdgeInsets safeArea) {
    return UIEdgeInsetsMake(
        MAX(0, safeArea.top - CGRectGetMinY(rootFrame)),
        MAX(0, safeArea.left - CGRectGetMinX(rootFrame)),
        MAX(0, CGRectGetMaxY(rootFrame) - (viewSize.height - safeArea.bottom)),
        MAX(0, CGRectGetMaxX(rootFrame) - (viewSize.width - safeArea.right)));
}

UIEdgeInsets ANNScreenPadding(UIEdgeInsets insets, ANNNavigationOptions *options,
                              ANNScreenPaddingContext context) {
    NSArray<NSString *> *edges = options.layout.safeAreaEdges;
    if (edges) {
        // Exactly the edges given; unknown names count for nothing (`layout.safeAreaEdges`).
        return UIEdgeInsetsMake([edges containsObject:@"top"] ? insets.top : 0,
                                [edges containsObject:@"left"] ? insets.left : 0,
                                [edges containsObject:@"bottom"] ? insets.bottom : 0,
                                [edges containsObject:@"right"] ? insets.right : 0);
    }
    UIEdgeInsets padding = insets;
    ANNTopBarOptions *topBar = options.topBar;
    BOOL topBarShown = [topBar.visible withDefault:YES];
    // A bar hidden for a while (`hideOnScroll`, `hideOnTap`, `hideOnKeyboard`) still counts as shown: only
    // `topBar.visible: false` gives the content its top back. A large title that can show takes the top too: the list
    // goes under the bar that collapses it.
    if (topBarShown && context.inStack &&
        (ANNScreenIsUnderTopBar(options) || ANNLargeTitleCanShow(options))) {
        padding.top = 0;
    }
    // The bottom is free: the content goes down to the bottom of the screen, under the home indicator and under a bar
    // at the bottom, and a list keeps its last rows clear with `useSafeAreaInsets()`.
    padding.bottom = 0;
    BOOL behindVerticalBar = [options.layout.drawBehindVerticalBar withDefault:YES] &&
                             [options.layout.verticalBars withDefault:YES];
    if (behindVerticalBar) {
        if (context.verticalBarSide == UIRectEdgeLeft) {
            padding.left = 0;
        } else if (context.verticalBarSide == UIRectEdgeRight) {
            padding.right = 0;
        }
    }
    return padding;
}
