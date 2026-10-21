#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The bar a pop starts from, as it is at the start: its bottom in the screen's coordinates (-1: none shows), whether
/// its screen has a large title, whether its look is clear at its scroll edge.
typedef struct {
    CGFloat bottom;
    BOOL largeTitle;
    BOOL clearAtEdge;
} ANNPopBar;

/// The large title of a screen (`topBar.largeTitle.*`). The mode goes into the item (`ANNLargeTitleDisplayMode`);
/// turned on for a shown screen only after `sizeToFit` of the bar and only for the top of its stack, turned off by one
/// change of the mode. `collapseOnScroll: false` gives the bar an empty scroll view for the top edge after the screen's
/// first appearance; while a pop reveals the screen on iOS 26 the title gets a place of its own in the window. On iOS
/// 26 a large title in the list with a solid `background.color` (not a clear one) and no React background paints the
/// color as a view inside the main list under its rows. Main queue.
@interface ANNLargeTitlePresenter : NSObject

- (instancetype)initWithScreen:(UIViewController *)screen;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// Whether the large title folds into the list as the content scrolls: it can show, `collapseOnScroll` isn't `false`,
/// and on iOS 26 React Native isn't right to left (it mirrors its lists, and the title would be drawn mirrored inside
/// them).
+ (BOOL)collapsesIntoList:(ANNNavigationOptions *)options;

/// The mode of `options` into the item. `shownTop`: the screen shows and is the top of its stack, so turning the large
/// title on sizes the bar first.
- (void)applyMode:(ANNNavigationOptions *)options shownTop:(BOOL)shownTop;

/// The scroll view the bar follows at the top edge. A title that doesn't fold into the list (`collapseOnScroll: false`,
/// right to left): the empty scroll view once the screen appeared. A title that folds into the list: `list`, the main
/// list the core found, so that UIKit doesn't take another scroll view, a hidden one at the screen's top. Otherwise the
/// bar chooses by itself again. While a pop runs with the screen's title in the place of
/// `screenInPop:list:inlineInRow:`: that place.
- (void)applyCollapse:(ANNNavigationOptions *)options list:(nullable UIScrollView *)list;

/// iOS 26: a pop or a swipe back starts that reveals the screen or takes it away. UIKit draws the screen's large
/// title, for the whole transition, inside the scroll view the bar follows at the top edge; the empty scroll view is
/// in no window, and a list below the bar (no main list at the screen's top) clips it. When the bar would follow one of
/// these and the title shows expanded, the screen's view gets a scroll view of the core over its content, at rest at
/// the top of the content (for a revealed screen, as it was covered), and the bar follows it until the screen appeared
/// or went (or the swipe was given up). A title that folds into its list shows expanded only when the bar took the
/// room of an expanded title (a search field that hides on scroll may have gone). Not for a covered screen whose size
/// is not the stack's (the window turned or resized meanwhile).
/// Called before UIKit's pop, or as UIKit's swipe starts, after the screen's will-appear.
- (void)screenInPop:(ANNNavigationOptions *)options
               list:(nullable UIScrollView *)list
        inlineInRow:(BOOL)inlineInRow;

/// The screen appeared: from now on `collapseOnScroll: false` takes effect at once; the place of the title of a pop
/// goes, and the bar follows what it follows at rest.
- (void)screenDidAppear:(ANNNavigationOptions *)options list:(nullable UIScrollView *)list;

/// The screen went without appearing (a swipe back given up): the place of the title of the pop goes, and the bar
/// follows what it followed before.
- (void)screenDidDisappear:(ANNNavigationOptions *)options list:(nullable UIScrollView *)list;

/// The pop ended for the screen that stays (a swipe back given up, the screen that was to leave): as
/// `screenDidDisappear:list:`.
- (void)popEnded:(ANNNavigationOptions *)options list:(nullable UIScrollView *)list;

/// The screen's title is in the place of `screenInPop:list:inlineInRow:` now.
@property(nonatomic, readonly) BOOL hostsTitle;

/// The screen shows at rest (appeared, laid out): the room the bar takes over its content is noted when it is the
/// largest seen, the room of an expanded title.
- (void)noteTopAtRest;

/// The place of the title of a pop leaves the screen's view (the screen leaves or is destroyed).
- (void)removeTitleHost;

/// The color of the bar in the main list; `inlineInRow`: the title is in the row, not in the list;
/// `backgroundComponent`: the screen has a React bar background, which takes that place instead.
- (void)updateListFill:(ANNNavigationOptions *)options
                  list:(nullable UIScrollView *)list
           inlineInRow:(BOOL)inlineInRow
   backgroundComponent:(BOOL)backgroundComponent;

/// The fill leaves the list (the screen leaves or is destroyed).
- (void)removeListFill;

/// iOS 26, a push, a pop or a swipe back from a screen with a lower bar: a large
/// title that doesn't fold into the list fills its place from `oldBarBottom` (the screen's coordinates) to the first
/// row, with `background.color`, or else with the bar's background (the system chrome material on a translucent bar,
/// the opaque system color otherwise), until the screen appeared. The fill lies in the list, or under the title in the
/// place of a pop's title while there is one. Without a list yet (the content renders during the push) the fill waits
/// for `updateTitlePlaceFill:list:`.
- (void)fillTitlePlaceFrom:(CGFloat)oldBarBottom
                      list:(nullable UIScrollView *)list
                   options:(ANNNavigationOptions *)options;

/// iOS 26, a pop or a swipe back from `bar`: the fill of `fillTitlePlaceFrom:list:options:` for a title that doesn't
/// fold into the list. Without a place of the title: as on a push, from a bar without a large title. With it, from
/// the screen's top (the bar's whole background while its look is clear), when the bar ends above the title's band (a
/// lower bar, a collapsed large title) or is clear at its edge; from any other bar, nothing.
- (void)fillTitlePlaceOfPopFrom:(ANNPopBar)bar
                           list:(nullable UIScrollView *)list
                        options:(ANNNavigationOptions *)options;

/// While the fill of a push is pending: places it again in the main list as it is now (after a mount, a layout).
- (void)updateTitlePlaceFill:(ANNNavigationOptions *)options list:(nullable UIScrollView *)list;

/// The fill of the push goes (the screen appeared).
- (void)removeTitlePlaceFill;

/// The fill of a pop lies under the title in the place of the pop's title: the band behind the title is the fill's.
- (BOOL)drawsTitleBand;

@end

NS_ASSUME_NONNULL_END
