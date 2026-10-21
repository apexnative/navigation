#import "ANNNativeScreenCalls.h"

@interface ANNNativeScreenCall : NSObject
@property(nonatomic, copy) NSString *name;
@property(nonatomic, copy) NSString *componentId;
@property(nonatomic, copy, nullable) ANNNativeScreenCallCompletion completion;
@end

@implementation ANNNativeScreenCall
@end

static NSMutableDictionary<NSString *, ANNNativeScreenCall *> *ANNPendingCalls(void) {
    static NSMutableDictionary *calls;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      calls = [NSMutableDictionary new];
    });
    return calls;
}

static NSUInteger ANNLastCallId = 0;

static NSError *ANNCallError(NSString *message) {
    return [NSError errorWithDomain:@"ApexNavigation"
                               code:1
                           userInfo:@{NSLocalizedDescriptionKey : message}];
}

@implementation ANNNativeScreenCalls

+ (NSString *)addCall:(NSString *)name
             ofScreen:(NSString *)componentId
           completion:(ANNNativeScreenCallCompletion)completion {
    NSString *callId = [NSString stringWithFormat:@"NativeScreenCall%lu", (unsigned long)++ANNLastCallId];
    ANNNativeScreenCall *call = [ANNNativeScreenCall new];
    call.name = name;
    call.componentId = componentId;
    call.completion = completion;
    ANNPendingCalls()[callId] = call;
    return callId;
}

+ (void)resolveCall:(NSString *)callId result:(NSDictionary *)result {
    id error = result[@"error"];
    if ([error isKindOfClass:NSString.class]) {
        [self finish:callId value:nil error:ANNCallError(error)];
        return;
    }
    id value = result[@"value"];
    [self finish:callId value:value == NSNull.null ? nil : value error:nil];
}

+ (void)failCall:(NSString *)callId reason:(NSString *)reason {
    [self finish:callId value:nil error:ANNCallError(reason)];
}

+ (void)failAllCalls:(NSString * (^)(NSString *name, NSString *componentId))reason {
    for (NSString *callId in ANNPendingCalls().allKeys) {
        ANNNativeScreenCall *call = ANNPendingCalls()[callId];
        if (call) {
            [self failCall:callId reason:reason(call.name, call.componentId)];
        }
    }
}

+ (void)finish:(NSString *)callId value:(id)value error:(NSError *)error {
    ANNNativeScreenCall *call = ANNPendingCalls()[callId];
    if (!call) {
        return;
    }
    [ANNPendingCalls() removeObjectForKey:callId];
    if (call.completion) {
        call.completion(value, error);
    }
}

@end
