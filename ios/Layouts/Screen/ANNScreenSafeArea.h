#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What the screen's containers answer for its padding.
typedef struct {
    /// The screen is in a stack.
    BOOL inStack;
    /// The physical side of the iPhone Duo vertical bar (iOS 27.1): `UIRectEdgeLeft`, `UIRectEdgeRight`, or
    /// `UIRectEdgeNone` without one.
    UIRectEdge verticalBarSide;
} ANNScreenPaddingContext;

/// The frame of the React root inside the screen's view: the whole width; from the top of the view when the screen is
/// under the top bar or no top bar shows, else from the top of the safe area; to the bottom of the view when it is
/// under the tab bar or no tab bar shows, else to the bottom of the safe area.
FOUNDATION_EXPORT CGRect ANNScreenRootFrame(CGRect bounds, UIEdgeInsets safeArea, BOOL fromTop,
                                            BOOL toBottom);

/// The insets of the root: the view's safe area over the root frame, each side at least 0.
FOUNDATION_EXPORT UIEdgeInsets ANNScreenInsetsOverRoot(CGRect rootFrame, CGSize viewSize,
                                                      UIEdgeInsets safeArea);

/// The padding the screen's safe area view keeps (`layout.safeAreaEdges`): the insets on the padded edges, 0 on the
/// others. With `layout.safeAreaEdges` exactly those edges (an explicit `'top'` keeps the top under a large title too);
/// without it every edge but: the top under a shown top bar (in a stack, under the top bar, `topBar.visible` not
/// `false`) or with a large title that can show; the bottom, always; the side of the vertical bar unless
/// `layout.drawBehindVerticalBar` or `layout.verticalBars` is `false`.
FOUNDATION_EXPORT UIEdgeInsets ANNScreenPadding(UIEdgeInsets insets, ANNNavigationOptions *options,
                                                ANNScreenPaddingContext context);

NS_ASSUME_NONNULL_END
