#import "ANNBarBackgroundComponent.h"
#import "ANNBarButtons.h"
#import "ANNLargeTitlePresenter.h"
#import "ANNNavigationOptions.h"
#import "ANNReactComponentRegistry.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The bar's part of one screen: what the screen's own `navigationItem` holds — its looks, titles, buttons, back
/// button, menus, search, large title, toolbar items, edge effect — and the React components of its bar, kept in the
/// screen's registry. The screen's presenter (`ANNComponentPresenter`) owns it and calls it at the screen's creation,
/// at each appearance (before the appearance animation), at a merge into the screen and at the repaint of new default
/// options. The parts the stack shares (visibility, hiding, bar style, the toolbar's visibility) are the stack's
/// (`ANNStackPresenter`), asked from here when the screen is UIKit's top of its stack. Main queue.
@interface ANNTopBarPresenter : NSObject

- (instancetype)initWithScreen:(UIViewController *)screen;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The bar's presenter of `screen` (a screen of the core); nil for any other node.
+ (nullable ANNTopBarPresenter *)presenterOfScreen:(UIViewController *)screen;

/// The screen's React roots of its bar components.
@property(nonatomic, strong, readonly) ANNReactComponentRegistry *registry;

/// The screen's React background, when its options have one.
@property(nonatomic, strong, readonly, nullable) ANNBarBackgroundComponent *background;

/// The React background goes into the main list or the bar's background view, by where the large title is: again once
/// the stack has put it into the bar outside a transition (a merge brings it to a shown screen).
- (void)updateBackgroundPlacement;

#pragma mark - The moments of the options

/// At the screen's creation, out of the stack, with its own options over the defaults (the stack's aren't known yet):
/// the title, subtitle, title menu and Rename, glass, large title mode, style, `verticalBarKeeps`, the buttons, the
/// looks, the React title and background.
- (void)applyOptionsOnInit:(ANNNavigationOptions *)options;

/// At each appearance, with the stack and the parents: all of the bar.
- (void)applyOptions:(ANNNavigationOptions *)options;

/// A merge into this screen: only the parts the merge names, with the values of the resolved options; nothing while
/// the screen isn't shown (it applies all at its appearance).
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions;

/// A merge into the screen's stack, which reaches the bar of its top screen (correction 7): as `mergeOptions:` but
/// nothing goes into the screen's own options (the stack's layer is under the screen's).
- (void)mergeParentOptions:(ANNNavigationOptions *)mergeOptions
           resolvedOptions:(ANNNavigationOptions *)resolvedOptions;

/// New default options: the looks, titles, buttons, search and the large title's list color; never the visibility of
/// the bar, the back button's visibility or the edge effect.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions;

#pragma mark - The screen's moments

/// The appearance moments of the screen: the bar components and the search hear them.
- (void)screenWillAppear;
- (void)screenDidAppear;
- (void)screenDidDisappear;

/// The screen laid out, or its content mounted (only in a window): the list fill, the pending fill of a push and the
/// React background in the list follow; a layout also places a `fill` title in its row, a mount the main list's edge
/// effect.
- (void)screenDidLayOut;
- (void)contentDidMount;

/// The screen's content is about to go (its destroy; a pop removes it before `viewDidDisappear`): its lists get the
/// system edge effect back and the large title's color leaves them; the place of the large title during a pop leaves
/// the screen. At `screenDidDisappear` the same but the edge effects, which stay while the screen is covered.
- (void)releaseLists;

/// Every root of the screen's bar still alive stops, once, on the main queue.
- (void)stopComponents;

#pragma mark - Waits and transitions

/// The roots of the bar components `options` name, made now when missing, and for each one not ready yet that a wait
/// counts (a title or background without `waitForRender: false`, a component button with an `id`): its id and whether
/// it is a button.
- (void)collectWaitedComponents:(void (^)(NSString *componentId, BOOL isButton))collect;

/// iOS 26, a push that shows the screen from a screen whose bar ended at `oldBarBottom` (the screen's coordinates) and
/// had no large title: a large title that doesn't fold into the list fills its place there until the screen appeared.
- (void)shownFromLowerBarWithBottom:(CGFloat)oldBarBottom;

/// iOS 26, a push starts that brings the screen in with its content drawn, before UIKit's push: its large title keeps
/// a place in the screen for the whole transition (`screenInPush:` of `ANNLargeTitlePresenter`). Nothing for a screen
/// that didn't wait parked for its content: its content comes in the push.
- (void)arrivesByPushOn:(UINavigationController *)navigation;

/// iOS 26, a pop or a swipe back starts that reveals the screen, after its will-appear and before UIKit lays it out
/// for the transition: its large title keeps a place in the window for the whole transition. Returns whether the title
/// is in that place.
- (BOOL)revealedByPop;

/// After `revealedByPop`: the band behind a title that doesn't fold into the list is filled from the bar the pop
/// starts from (`bar`); while that fill lies under the title the bar's edge look is clear.
- (void)revealedByPopFrom:(ANNPopBar)bar;

/// Whether the screen's look leaves the bar clear at its scroll edge (a large title in the list on iOS 26).
- (BOOL)clearsBarAtEdge;

/// iOS 26, a pop or a swipe back starts that takes the screen away, before the revealed screen's band: the leaving
/// large title keeps a place in the window too, and a title that doesn't fold into the list fills its band there from
/// the screen's top, so that its band leaves with it as at rest. `clearsEdge` (the revealed screen's title has its
/// place, `revealedByPop`): the bar's edge look is clear meanwhile, so that the bar doesn't draw the leaving band over
/// the revealed title; otherwise the look stays, under the revealed screen's bar.
- (void)leavesByPopClearingEdge:(BOOL)clearsEdge;

/// The swipe back was given up: the screen that was to leave shows on as before the swipe.
- (void)popGivenUp;

/// iOS 26, a push that isn't interactive: the incoming title's color is the one resolved for `traits` until
/// `coordinator` ends, through every look applied meanwhile (a merge, a change of the defaults).
- (void)pinTitleColorAlong:(id<UIViewControllerTransitionCoordinator>)coordinator
                    traits:(UITraitCollection *)traits;

@end

NS_ASSUME_NONNULL_END
