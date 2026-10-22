#import "ANNNativeScreenContext+Internal.h"
#import "ANNErrorTexts.h"
#import "ANNEventContract.h"
#import "ANNEventEmitter.h"
#import "ANNNativeScreenCalls.h"
#import <React/RCTUtils.h>

@implementation ANNNativeScreenContext {
    __weak ANNEventEmitter *_eventEmitter;
}

- (instancetype)initWithComponentId:(NSString *)componentId
                              props:(NSDictionary *)props
                          functions:(NSArray<NSString *> *)functions
                       eventEmitter:(ANNEventEmitter *)eventEmitter {
    if (self = [super init]) {
        _componentId = [componentId copy];
        _props = [props copy] ?: @{};
        _functions = [NSSet setWithArray:functions ?: @[]];
        _eventEmitter = eventEmitter;
    }
    return self;
}

- (void)applyUpdate:(NSDictionary *)update {
    NSMutableDictionary *props = [_props mutableCopy];
    NSDictionary *changed = update[@"props"];
    if ([changed isKindOfClass:NSDictionary.class]) {
        [props addEntriesFromDictionary:changed];
    }
    NSArray *removed = update[@"removed"];
    if ([removed isKindOfClass:NSArray.class]) {
        [props removeObjectsForKeys:removed];
    }
    _props = [props copy];
    NSArray *functions = update[@"functions"];
    if ([functions isKindOfClass:NSArray.class]) {
        _functions = [NSSet setWithArray:functions];
    }
    if (_onPropsChange) {
        _onPropsChange(_props);
    }
}

- (void)call:(NSString *)function
     arguments:(NSArray *)arguments
    completion:(ANNNativeScreenCallCompletion)completion {
    [self send:@"function" name:function arguments:arguments completion:completion];
}

- (void)navigate:(NSString *)command
       arguments:(NSArray *)arguments
      completion:(ANNNativeScreenCallCompletion)completion {
    [self send:@"command" name:command arguments:arguments completion:completion];
}

// The call waits for its answer under its id; the event takes it to JS.
- (void)send:(NSString *)kind
          name:(NSString *)name
     arguments:(NSArray *)arguments
    completion:(ANNNativeScreenCallCompletion)completion {
    RCTExecuteOnMainQueue(^{
      NSString *callId = [ANNNativeScreenCalls addCall:name
                                              ofScreen:self->_componentId
                                            completion:completion];
      ANNEventEmitter *eventEmitter = self->_eventEmitter;
      // No emitter, or one of a JavaScript that went (a screen held for the reload's 2 s): nobody would answer, so the
      // call fails at once.
      if (!eventEmitter || !eventEmitter.isCurrent) {
          [ANNNativeScreenCalls failCall:callId
                                  reason:[ANNErrorTexts nativeCallNotRunning:name
                                                                 componentId:self->_componentId]];
          return;
      }
      const auto &keys = ANNEventContract.NativeScreenCall;
      [eventEmitter sendNativeScreenCall:@{
          keys.componentId : self->_componentId,
          keys.callId : callId,
          keys.kind : kind,
          keys.name : name,
          keys.args : arguments ?: @[],
      }];
    });
}

@end
