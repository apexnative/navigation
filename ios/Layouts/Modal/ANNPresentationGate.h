#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// One call to UIKit's present or dismiss that waits for its turn at the gate (`ANNPresentationGate`). `run` gets
/// `done`, which the step calls once, when UIKit called its completion (or at once when it asked UIKit for nothing);
/// until then the gate is busy.
@interface ANNGateStep : NSObject

- (instancetype)initWithRun:(void (^)(dispatch_block_t done))run NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The root whose presentation chain must not be in a transition when the step runs; nil: the main window's root.
/// The reload's step names the old root, which is no longer the window's.
@property(nonatomic, copy, nullable) UIViewController *_Nullable (^transitionRoot)(void);

/// Whether the step can run now (N2: the controller it presents from is in a shown window); nil: always. A step that
/// can't stays at the head until the gate is told that something changed (`pump`).
@property(nonatomic, copy, nullable) BOOL (^canRun)(void);

/// The step went from the queue before it ran (its record went): it never runs.
@property(nonatomic, readonly) BOOL dropped;

@end

/// The one queue of every UIKit presentation and dismissal the core asks for in the main window: the modals' shows and
/// dismissals, the cleaning of the top of UIKit's chain, the root's replacement over a chain, the reload's dismissal of
/// the old chain, the search controller before its modal. UIKit drops a transition asked for while another runs and
/// never calls its completion; so a step runs only at the head of the queue, when no call of the gate waits for its
/// completion, and when no presentation or dismissal runs over the root (its transition coordinator's completion is
/// waited for; the step runs on the next turn of the main queue, after UIKit finished its part). Steps run in the order
/// they were asked; nothing waits by time. Main queue.
@interface ANNPresentationGate : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `step` goes to the end of the queue; it runs now when the gate is open and nothing waits before it.
+ (void)enqueue:(ANNGateStep *)step;

/// A queued step leaves the queue without running; nothing for a step that ran already.
+ (void)drop:(ANNGateStep *)step;

/// Something a waiting step may wait for changed (the main window got its scene, the window's root changed): the head
/// is tried again.
+ (void)pump;

@end

NS_ASSUME_NONNULL_END
