#import "ANNCommandAnswer.h"
#import "ANNLayoutNode.h"
#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

@class ANNOverlay;

NS_ASSUME_NONNULL_BEGIN

/// The overlays of the app (`showOverlay`, `dismissOverlay`, `dismissAllOverlays`): each in a window of its own
/// (`ANNOverlayWindow`) in the main window's scene, a later one above an earlier one, in the order shown. An overlay
/// waits for its screens (as a modal without options), shows without animation and goes at once. Overlays are no
/// modals: no gate, no part of the navigation state, kept over a new root. Main queue.
@interface ANNOverlayCoordinator : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

#pragma mark - Commands

/// `showOverlay` after the runner's shared steps: the types the core makes, "shows already" (an overlay with the id of
/// a shown overlay replaces it), the options; the tree is made, waits for its screens, shows in its window, answers
/// with its root's id.
+ (void)showOverlay:(ANNLayoutNode *)tree answer:(ANNCommandAnswer *)answer;

/// `dismissOverlay`: the overlay of `componentId` (its root's id or an id inside it), at once; answers its id.
+ (void)dismissOverlay:(NSString *)componentId answer:(ANNCommandAnswer *)answer;

/// `dismissAllOverlays`: every overlay, last to first; answers `""`.
+ (void)dismissAllOverlays:(ANNCommandAnswer *)answer;

#pragma mark - setRoot

/// An overlay of a `setRoot` layout, made now with `options` (by node id): it waits and shows as any overlay (a shown overlay
/// with its id goes, N5), without an answer. `ended` runs once: YES when it showed, NO when it failed (logged); never
/// when `cancelChainOverlay:` or a reload took it. Nil when it couldn't be made (logged, `ended` ran).
+ (nullable ANNOverlay *)showChainOverlay:(ANNLayoutNode *)tree
                                  options:(NSDictionary<NSString *, ANNNavigationOptions *> *)options
                                    ended:(void (^)(BOOL shown))ended;

/// The `setRoot` that showed `overlay` was superseded while it waited: it never shows, nothing is sent.
+ (void)cancelChainOverlay:(ANNOverlay *)overlay;

/// The root's id of the overlay `node` is in (its root or a node inside it), shown or waiting; nil for a node of no
/// overlay. The question "is this node in an overlay" of the system UI and of the "shows already" checks.
+ (nullable NSString *)overlayRootIdOf:(nullable UIViewController *)node;

#pragma mark - The windows

/// The windows of the shown overlays, bottom to top (the repaint).
+ (NSArray<UIWindow *> *)overlayWindows;

/// The main window connected to its scene: an overlay window made before joins it.
+ (void)mainWindowSceneDidConnect:(UIWindowScene *)windowScene;

/// `mergeOptions` merged `options` into `node`: the overlay's `interceptTouchOutside` is read again and kept, its
/// `testID` too.
+ (void)optionsMerged:(ANNNavigationOptions *)options into:(UIViewController *)node;

/// A reload of JavaScript: every overlay window hides at once, the main window is key and seen by VoiceOver again; the
/// overlays' roots come back to join the old screens. Nothing is sent.
+ (NSArray<UIViewController *> *)takeAllForReload;

@end

NS_ASSUME_NONNULL_END
