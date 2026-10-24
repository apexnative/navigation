#import "ANNJavaScriptCalls.h"
#import "ANNAssert.h"
#import <React/RCTCallInvoker.h>
#import <ReactCommon/CallInvoker.h>

// The module told last, weakly: a module that went takes its invoker with it.
static __weak id ANNCallsModule;
static RCTCallInvoker *ANNCallsInvoker;

@implementation ANNJavaScriptCalls

+ (void)setCallInvoker:(RCTCallInvoker *)callInvoker ofModule:(id)module {
    @synchronized(self) {
        ANNCallsModule = module;
        ANNCallsInvoker = callInvoker;
    }
}

+ (void)moduleWent:(id)module {
    @synchronized(self) {
        if (ANNCallsModule != module) {
            return;
        }
        ANNCallsModule = nil;
        ANNCallsInvoker = nil;
    }
}

+ (void)afterCallsMadeSoFar:(dispatch_block_t)block {
    ANNAssertMainQueue();
    RCTCallInvoker *callInvoker = nil;
    @synchronized(self) {
        if (ANNCallsModule) {
            callInvoker = ANNCallsInvoker;
        }
    }
    std::shared_ptr<facebook::react::CallInvoker> invoker =
        callInvoker ? callInvoker.callInvoker : nullptr;
    if (!invoker) {
        block();
        return;
    }
    dispatch_block_t done = [block copy];
    // The JavaScript thread runs the task after the one it runs now, which made the calls of this moment (a commit of
    // React and its layout effects). Each of those calls went to the main queue as it was made, so this hop, sent from
    // the same thread later, reaches the main queue behind them (a serial queue, in the order it was given).
    invoker->invokeAsync([done](facebook::jsi::Runtime &) {
        dispatch_async(dispatch_get_main_queue(), done);
    });
}

@end
