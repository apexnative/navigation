#import "ANNJavaScriptCalls.h"
#import "ANNAssert.h"
#import <React/RCTCallInvoker.h>
#import <React/RCTUtils.h>
#import <ReactCommon/CallInvoker.h>

// The module told last, weakly: a module that went takes its invoker and its queue with it.
static __weak id ANNCallsModule;
static RCTCallInvoker *ANNCallsInvoker;
static dispatch_queue_t ANNCallsQueue;

@implementation ANNJavaScriptCalls

+ (void)setCallInvoker:(RCTCallInvoker *)callInvoker
           methodQueue:(dispatch_queue_t)methodQueue
              ofModule:(id)module {
    @synchronized(self) {
        ANNCallsModule = module;
        ANNCallsInvoker = callInvoker;
        ANNCallsQueue = methodQueue;
    }
}

+ (void)moduleWent:(id)module {
    @synchronized(self) {
        if (ANNCallsModule != module) {
            return;
        }
        ANNCallsModule = nil;
        ANNCallsInvoker = nil;
        ANNCallsQueue = nil;
    }
}

+ (void)afterCallsMadeSoFar:(dispatch_block_t)block {
    ANNAssertMainQueue();
    RCTCallInvoker *callInvoker = nil;
    dispatch_queue_t queue = nil;
    @synchronized(self) {
        if (ANNCallsModule) {
            callInvoker = ANNCallsInvoker;
            queue = ANNCallsQueue;
        }
    }
    std::shared_ptr<facebook::react::CallInvoker> invoker =
        callInvoker ? callInvoker.callInvoker : nullptr;
    if (!invoker || !queue) {
        block();
        return;
    }
    dispatch_block_t done = [block copy];
    // The JavaScript thread runs the task after the one it runs now, which made the calls of this moment (a commit of
    // React and its layout effects); the module's queue then has those calls before this hop, and the main queue too.
    invoker->invokeAsync([queue, done](facebook::jsi::Runtime &) {
        dispatch_async(queue, ^{
          RCTExecuteOnMainQueue(done);
        });
    });
}

@end
