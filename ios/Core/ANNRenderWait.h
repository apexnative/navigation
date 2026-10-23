#import "ANNBarComponentsWait.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What content a render wait waits for.
typedef NS_ENUM(NSInteger, ANNRenderWaitContent) {
    /// No content: the bar components alone.
    ANNRenderWaitContentNone,
    /// Each screen of `screens` whose React content isn't ready, by its own ready handler (a root's screens). The limit
    /// marks ready the screens still waited.
    ANNRenderWaitContentEachScreen,
    /// `node` as a whole, by its ready handler (a screen, or a pair once both of its screens are), and after `setUp` by
    /// the test that every screen of `screens` has no content to wait for or its content is ready (a stack's entry).
    /// The limit marks ready every screen of `screens`.
    ANNRenderWaitContentNode,
};

/// How a render wait waits for the bar components of `screens` (title, background, component buttons).
typedef NS_ENUM(NSInteger, ANNRenderWaitBar) {
    /// Not at all.
    ANNRenderWaitBarNone,
    /// Together with the content, within the wait's limit, and each kind within `barLimits` (0: the wait's limit only).
    ANNRenderWaitBarWithContent,
    /// A phase of their own once the content is there, each kind within `barLimits` from the phase's start.
    ANNRenderWaitBarAfterContent,
};

/// The one wait for the render of what a command shows: the content of some screens within a limit or none, `markReady`
/// at the limit (their held events go at once); the bar components within the limit or as a phase after the content;
/// the rule for content of unknown readiness. A token makes every ready handler, limit and bar callback of an earlier
/// wait or phase do nothing. The owner says what it waits for and what runs at the end; a root's `setRoot`, a stack's
/// push and `setStackRoot`, and the show of a modal and of an overlay use it. Each wait listens under a ready handler
/// key of its own (`ANNNodeHelper setReadyHandler:forKey:`). Main queue.
@interface ANNRenderWait : NSObject

/// `screens` gives the screens the wait is about: their bar components, the screens the limit marks ready, the screens
/// of the unknown-content rule, and with `ANNRenderWaitContentEachScreen` the screens whose content it waits for, each
/// by the readiness it needs (`ANNWaitedReadinessOf`: content, or "root mounted" without its bar components). It is
/// asked as the wait starts, as it takes its screens again (`retakeScreens:`) and as its bar phase starts. `node` is
/// the node of `ANNRenderWaitContentNode`; nil otherwise. `limit` 0: no limit.
- (instancetype)initWithContent:(ANNRenderWaitContent)content
                           node:(nullable UIViewController *)node
                        screens:(NSArray<UIViewController *> * (^)(void))screens
                          limit:(NSTimeInterval)limit
                            bar:(ANNRenderWaitBar)bar
                      barLimits:(ANNBarWaitLimits)barLimits NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The longest a waited screen of the "root mounted" kind (`ANNWaitedReadinessOf`, an unselected tab's screen) is
/// waited for, from the start, also when the wait has no limit: a screen the user doesn't see never holds a command for
/// long. 0: only the wait's own limit.
@property(nonatomic) NSTimeInterval mountLimit;

/// The rule for content of unknown readiness: 1 s after the start, a screen whose rendered state stays unknown (no safe
/// area view says it) counts as ready; a screen whose safe area view spoke waits for its signal. Off by default (a root
/// has none).
@property(nonatomic) BOOL unknownContentRule;

/// The content phase ends only once every call JavaScript made by then is applied on the main queue
/// (`ANNJavaScriptCalls`): the options a screen merges in the commit that mounted it are there before the owner's next
/// step. Also when the content is there already as the wait starts or takes its screens again (the commit that mounted
/// it may have run just now): such a phase ends after `start`, through `protect`, while JavaScript has a call invoker;
/// without one (no JavaScript running) the calls are applied already and the phase ends inside `start`, as without
/// this. Screens without React content (a native screen) end the phase at once. The limit still ends the phase
/// meanwhile. Off by default.
@property(nonatomic) BOOL settlesThroughJavaScript;

/// With `ANNRenderWaitBarAfterContent`, the bar phase that follows the content phase counts its limits from the wait's
/// start, not from its own: the time the content took comes off each kind's limit, and a kind past it by then waits no
/// more. A bar phase that starts again (`retakeScreens:`) counts from then, as without it. Off by default.
@property(nonatomic) BOOL barLimitsFromStart;

/// Runs inside `start`, after the ready handlers are set and before the readiness test (the stack parks and renders
/// its entry there). A ready handler that runs inside it is left to the test after it.
@property(nonatomic, copy, nullable) dispatch_block_t setUp;

/// Asked each time the wait would end or go on to its bar phase: NO leaves it waiting as it is (a stack's operation
/// that ended or belongs to an old JavaScript). Nil: always YES.
@property(nonatomic, copy, nullable) BOOL (^canEnd)(void);

/// The owner's protection of the steps that come later (a ready handler, a limit, the bar components, `endPhase`,
/// `retakeScreens:`): it gets each step that ends the wait or starts its bar phase, and runs it. Steps inside `start`
/// run as they are, under the caller's own protection. Nil: steps run as they are.
@property(nonatomic, copy, nullable) void (^protect)(dispatch_block_t step);

/// Runs once, when the wait ends by itself (never after `cancel`), after it let go of everything.
@property(nonatomic, copy, nullable) dispatch_block_t ended;

/// The wait starts: the bar components that go with the content, the ready handlers, `setUp`, the readiness test, and
/// the limits. It may end inside (nothing to wait for).
- (void)start;

/// The wait runs (also in its bar phase).
@property(nonatomic, readonly) BOOL isWaiting;

/// The wait runs its bar phase (`ANNRenderWaitBarAfterContent`, the content being there).
@property(nonatomic, readonly) BOOL isInBarPhase;

/// What the waited place shows changed (a stack command inside a waiting root): in the content phase the wait lets go
/// of what it waited for and, when `take`, takes `screens` again (with the bar components that go with them) with the
/// same token, so that its limit stays; it ends when nothing is left. In the bar phase the phase starts again, with its
/// limits from now. `take` NO: the place went (destroyed); the content phase takes nothing. Nothing when it doesn't
/// run, and nothing when `take` would take the same screens as the phase's last take, in the content phase each with
/// the same readiness, content, readiness of its content and first mount.
- (void)retakeScreens:(BOOL)take;

/// The running phase ends now, as if what it waits for were there, without `markReady` (a stack that left the screen):
/// the content phase goes on to the bar phase, the bar phase ends the wait. Through `canEnd` and `protect`.
- (void)endPhase;

/// The wait ends without `ended`: its handlers, its bar wait and its limits do nothing from now on.
- (void)cancel;

@end

NS_ASSUME_NONNULL_END
