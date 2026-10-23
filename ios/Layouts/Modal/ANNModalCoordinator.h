#import "ANNCommandAnswer.h"
#import "ANNLayoutNode.h"
#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

@class ANNModal;

NS_ASSUME_NONNULL_BEGIN

/// The modals of the app: one record per modal (`ANNModal`), in the order of their shows, the newest last. Every rule
/// about modals is a move of one record (its state, its waiting commands), and every UIKit present or dismiss goes
/// through `ANNPresentationGate`. Nothing else keeps a list of modals: the root coordinator, the navigation state, the
/// repaint and the reload ask here. Main queue.
@interface ANNModalCoordinator : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

#pragma mark - Commands

/// `showModal` after the runner's shared steps: the types the core makes, "shows already", the options of the tree;
/// then the tree is made and rendered in the window's bounds, the record waits for its screens, presents at its turn,
/// and answers with the root's id after the state.
+ (void)showModal:(ANNLayoutNode *)tree answer:(ANNCommandAnswer *)answer;

/// `dismissModal`: the record of `componentId` (its root's id or any id inside, also of a modal closed below or by the
/// user), the options merged into its visible screen, the animation from the command's screen, then its state decides.
/// Answers with the root's id. The last modal over no root rejects `[ANNErrorTexts modalWithoutRoot:]`, as on Android.
+ (void)dismissModal:(NSString *)componentId
             options:(nullable NSDictionary *)options
              answer:(ANNCommandAnswer *)answer;

/// `dismissAllModals`: every modal in one transition of the top one; `options` give only
/// `animations.dismissModal.exit.enabled`. Answers `""`.
+ (void)dismissAllModals:(nullable NSDictionary *)options answer:(ANNCommandAnswer *)answer;

#pragma mark - setRoot

/// A modal of a `setRoot` layout, made now with `options` (by node id): it waits and presents as any modal, without
/// an answer.
/// `animated` NO shows it without animation; YES by its own options (the last one). `ended` runs once: YES when it
/// showed, NO when it failed (logged) or went before it showed; never when `cancelChainModal:` or a reload took it. Nil
/// when it couldn't be made (logged, `ended` ran).
+ (nullable ANNModal *)showChainModal:(ANNLayoutNode *)tree
                              options:(NSDictionary<NSString *, ANNNavigationOptions *> *)options
                             animated:(BOOL)animated
                                ended:(void (^)(BOOL shown))ended;

/// The `setRoot` that showed `modal` was superseded while it waited: it never shows, nothing is sent.
+ (void)cancelChainModal:(ANNModal *)modal;

/// A `setRoot` was accepted (`setRoot` step 3): the waiting modals never show; the others go with the old root
/// (commands by their ids find nothing); a dragged one goes with it unless the drag closes it; one being dismissed
/// finishes its dismissal.
+ (void)takeForNewRoot;

/// The `setRoot` that took the modals ended without replacing the root: they are what they were.
+ (void)restoreTakenForNewRoot;

/// The `setRoot` replaces the root: the list is emptied. Returns the roots of the modals that go with the old root,
/// bottom to top; the root coordinator reports their disappearance and destroys them with the old root (no
/// `ANN.ModalDismissed`). Their waiting commands and shows resolve with their roots' ids.
+ (NSArray<UIViewController *> *)takeAtReplacement;

/// A reload of JavaScript: every record goes without events and answers; their waits stop and the waiting views leave
/// the window. Returns their roots, which join the old screens.
+ (NSArray<UIViewController *> *)takeAllForReload;

#pragma mark - Questions

/// The layout the user sees: the top modal that is presenting, shown, dragged or being dismissed, else the window's
/// root when it is a node; overlays and foreign controllers never.
+ (nullable UIViewController *)visibleLayout;

/// Whether `node` is in a modal that goes with the old root: a command by its id finds nothing.
+ (BOOL)isWithOldRoot:(UIViewController *)node;

/// The roots of the modals, bottom to top, for the repaint of the default options.
+ (NSArray<UIViewController *> *)modalRoots;

#pragma mark - What changes in a modal

/// `mergeOptions` merged `options` into `node` (`mergeOptions` step 5): a modal's sheet and size take the merge, its
/// hold and its `testID` follow.
+ (void)optionsMerged:(ANNNavigationOptions *)options into:(UIViewController *)node;

/// What `node` shows changed (a stack operation ended, a tab switch): the hold follows the modal's visible screen.
+ (void)visibleScreenChangedIn:(UIViewController *)node;

#pragma mark - What the record hears from UIKit

+ (void)modalWillBeDraggedAway:(ANNModal *)modal;
+ (void)modalWasClosedByUser:(ANNModal *)modal;
+ (void)userTriedToClose:(ANNModal *)modal;
+ (void)modal:(ANNModal *)modal detentChanged:(id)detent;

@end

NS_ASSUME_NONNULL_END
