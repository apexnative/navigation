#import <Foundation/Foundation.h>

@class RCTCallInvoker;

NS_ASSUME_NONNULL_BEGIN

/// The way of the calls JavaScript makes to `ANNTurboModule`, for a step that must come after them. A call without an
/// answer (`mergeOptions`) leaves the JavaScript thread as it is made, goes to the module's queue (React Native's
/// `ModuleNativeMethodCallInvoker`, a `dispatch_async` to it) and from there to the main queue (`ANNCommandRunner`):
/// a step that takes the same three hops after them comes after them on the main queue, as each of the three runs its
/// work in order. The module tells its call invoker and its queue when React Native made it, and that it went when
/// React Native invalidates it (a reload). Any thread, except `afterCallsMadeSoFar:`.
@interface ANNJavaScriptCalls : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The module of the JavaScript that runs now, its call invoker (the JavaScript thread) and its queue.
+ (void)setCallInvoker:(nullable RCTCallInvoker *)callInvoker
           methodQueue:(nullable dispatch_queue_t)methodQueue
              ofModule:(id)module;

/// The module went: with no newer module told since, no JavaScript runs to wait for.
+ (void)moduleWent:(id)module;

/// `block` runs on the main queue once every call JavaScript made to the module before the next task of its thread is
/// applied on the main queue: a task on the JavaScript thread, from there one on the module's queue, from there one on
/// the main queue. With no JavaScript to ask (none yet, a reload), at once. Main queue. A JavaScript thread that never
/// runs the task again (its runtime went) never runs `block`: a caller that can't wait for long has a limit of its own.
+ (void)afterCallsMadeSoFar:(dispatch_block_t)block;

@end

NS_ASSUME_NONNULL_END
