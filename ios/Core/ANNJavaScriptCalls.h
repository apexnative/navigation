#import <Foundation/Foundation.h>

@class RCTCallInvoker;

NS_ASSUME_NONNULL_BEGIN

/// The way of the calls JavaScript makes to `ANNTurboModule`, for a step that must come after them. A call without an
/// answer (`mergeOptions`) leaves the JavaScript thread as it is made, as a task on the main queue, the module's queue
/// (React Native's `ModuleNativeMethodCallInvoker`, a `dispatch_async` to it), and runs whole in that task
/// (`ANNCommandRunner`). A step sent from the JavaScript thread to the main queue after them comes after them: the
/// JavaScript thread runs its tasks in order, and the main queue runs what it is given in the order it was given. The
/// module tells its call invoker when React Native made it, and that it went when React Native invalidates it (a
/// reload). Any thread, except `afterCallsMadeSoFar:`.
@interface ANNJavaScriptCalls : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The module of the JavaScript that runs now and its call invoker (the JavaScript thread).
+ (void)setCallInvoker:(nullable RCTCallInvoker *)callInvoker ofModule:(id)module;

/// The module went: with no newer module told since, no JavaScript runs to wait for.
+ (void)moduleWent:(id)module;

/// `block` runs on the main queue once every call JavaScript made to the module before the next task of its thread is
/// applied: a task on the JavaScript thread, from there one on the main queue. With no JavaScript to ask (none yet, a
/// reload), at once. Main queue. A JavaScript thread that never runs the task again (its runtime went) never runs
/// `block`: a caller that can't wait for long has a limit of its own.
+ (void)afterCallsMadeSoFar:(dispatch_block_t)block;

@end

NS_ASSUME_NONNULL_END
