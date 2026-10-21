#import <UIKit/UIKit.h>

@class ANNCommandAnswer;
@class ANNGateStep;
@class ANNModalAnimationOptions;
@class ANNModalBatch;
@class ANNModalPresentation;
@class ANNModalTransitions;
@class ANNParking;
@class ANNRenderWait;

NS_ASSUME_NONNULL_BEGIN

/// The state of a modal: every rule about modals is a move of one record between these.
typedef NS_ENUM(NSInteger, ANNModalState) {
    /// Made and findable; waits for its screens, then for its turn at the gate. Its view waits in the window, unseen.
    ANNModalStateWaiting,
    /// UIKit presents it; its `WillAppear` went.
    ANNModalStatePresenting,
    /// On screen; its show answered.
    ANNModalStateShown,
    /// Shown, and the user drags it away (`presentationControllerWillDismiss:` until the drag ends).
    ANNModalStateGesture,
    /// A dismissal was accepted: it waits for its turn at the gate, or UIKit dismisses it.
    ANNModalStateDismissing,
    /// Dismissed while a modal above it stayed: reported, its screens went; its controller stays in UIKit's chain with
    /// its view hidden until UIKit removes it with the ones above.
    ANNModalStateClosedBelow,
    /// A `setRoot` was accepted: commands by its ids find nothing; it goes with the old root.
    ANNModalStateWithOldRoot,
    /// Out of the list.
    ANNModalStateGone,
};

/// A command's dismissal of a modal, or Android's back on it: its answer (none for the user's), its animation.
@interface ANNModalDismissal : NSObject
@property(nonatomic, strong, nullable) ANNCommandAnswer *answer;
@property(nonatomic) BOOL animated;
/// `animations.dismissModal` of the command's screen with its parents and the defaults, read at the command: the custom
/// dismissal's plan is made from it when the dismissal starts.
@property(nonatomic, strong, nullable) ANNModalAnimationOptions *animation;
@end

/// One modal: its root, its state and the commands that wait for it. It is the delegate of its presentation: what UIKit
/// tells of the user's closes, the tries on a held modal and the detents the finger chose goes to
/// `ANNModalCoordinator`. Only the coordinator changes it. Main queue.
@interface ANNModal : NSObject <UIAdaptivePresentationControllerDelegate,
                                UISheetPresentationControllerDelegate>

- (instancetype)initWithRoot:(UIViewController *)root
                      rootId:(NSString *)rootId NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The node of the modal's layout (any node), held until the record goes.
@property(nonatomic, strong, readonly) UIViewController *root;
@property(nonatomic, copy, readonly) NSString *rootId;

@property(nonatomic) ANNModalState state;
/// While `ANNModalStateWithOldRoot`: the state it had, which UIKit, the user and the state of the navigation still see.
@property(nonatomic) ANNModalState stateBeforeRoot;

/// The answer of its `showModal`, answered once; nil in a `setRoot` chain.
@property(nonatomic, strong, nullable) ANNCommandAnswer *showAnswer;
/// The `setRoot` chain's next step: YES when the modal showed, NO when it failed.
@property(nonatomic, copy, nullable) void (^chainStep)(BOOL shown);
/// The animation of its present when the chain decides it (a lower modal of a layout: none); nil: by its options.
@property(nonatomic, strong, nullable) NSNumber *animatedOverride;

/// `ANN.ModalDismissed` went for it: never twice.
@property(nonatomic) BOOL reported;
/// The decisions of its show, read once at the present.
@property(nonatomic, strong, nullable) ANNModalPresentation *presentation;
/// The layer under it at its present (the root of the record below it, or the window's root).
@property(nonatomic, weak, nullable) UIViewController *layerUnder;
/// Its transitioning delegate while it has a custom show or dismissal (UIKit holds the delegate weakly).
@property(nonatomic, strong, nullable) ANNModalTransitions *transitions;
/// When its own wait started (`waitForRender` unset): a custom show waits what is left of the 0.5 s; 0 when it didn't
/// wait so.
@property(nonatomic) CFTimeInterval waitStarted;
/// The layer under it disappeared at its show (UIKit took the presenting view out of the window).
@property(nonatomic) BOOL coversUnder;
/// The ids of every node of its tree, taken when it closes below, goes with the old root or is closed by the user:
/// its nodes may be destroyed by then.
@property(nonatomic, copy) NSSet<NSString *> *ids;

/// The wait for its screens, and where its view waits meanwhile.
@property(nonatomic, strong, nullable) ANNRenderWait *wait;
@property(nonatomic, strong, readonly) ANNParking *parking;
/// Its wait ended: it presents at its turn.
@property(nonatomic) BOOL ready;

/// Its step queued at the gate (its present, its dismissal), until it runs.
@property(nonatomic, strong, nullable) ANNGateStep *step;
/// The dismissal it runs (`ANNModalStateDismissing`), and whether UIKit's call for it started.
@property(nonatomic, strong, nullable) ANNModalDismissal *dismissal;
@property(nonatomic) BOOL dismissalStarted;
/// The commands that wait for its transition (a dismissal during its show, its drag, its dismissal), in order.
@property(nonatomic, strong, readonly) NSMutableArray<ANNModalDismissal *> *pending;

/// The `dismissAllModals` it goes with.
@property(nonatomic, weak, nullable) ANNModalBatch *batch;
/// A `setRoot` was accepted while the user dragged it: it goes with the old root unless the drag closes it.
@property(nonatomic) BOOL takenWithDrag;
/// The layer under that the user's drag reveals (it had disappeared): the core reports its appearance from the drag's
/// start, so that its `DidAppear` comes after the modal's `DidDisappear` when the drag closes it.
@property(nonatomic, weak, nullable) UIViewController *dragRevealed;

/// The detent the sheet stands at as far as the core knows (`selectedDetent`, else the lowest, at the show and at a
/// merge of `selectedDetent`; then the user's): `ANN.SheetDetentChanged` goes only for another one.
@property(nonatomic, strong, nullable) id knownDetent;
/// The presentation's container view a modal closed below hid: UIKit's sheet card doesn't stay seen.
@property(nonatomic, weak, nullable) UIView *hiddenContainer;

/// The nodes the command's transition reports itself (`driveAppearanceOf:`), while `driven` is set.
@property(nonatomic, copy, nullable) NSArray<UIViewController *> *driven;

@end

NS_ASSUME_NONNULL_END
