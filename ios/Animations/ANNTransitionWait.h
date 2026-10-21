#import <Foundation/Foundation.h>

@class ANNTransitionPlan;

NS_ASSUME_NONNULL_BEGIN

/// The wait before a custom forward transition: the start comes when the destination's screens have their content
/// (`isReady` of their content, the children fallback included) and every `toId` of the shared pairs and every `id` of
/// the elements is found. The test runs at every mount of the destination screens' own content (their content-mount
/// handlers: a React view filters its surface by `rootTag`, a slot screen hears its host), once 0,1 s after the wait
/// starts, and the start comes at the plan's `waitLimit` at the latest; destination screens without React content
/// (native screens) start at once. The handlers are set once and always removed: at the start, at the cancel, and when
/// the wait goes. Main queue.
@interface ANNTransitionWait : NSObject

- (instancetype)initWithPlan:(ANNTransitionPlan *)plan NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The wait starts; `start` runs once when it ends (maybe inside this call, when nothing is to wait for).
- (void)startThen:(dispatch_block_t)start;

/// The wait ends without `start` (a destroy, a reload).
- (void)cancel;

@end

NS_ASSUME_NONNULL_END
