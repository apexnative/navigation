#import "ANNCommandAnswer.h"
#import "ANNAssert.h"
#import "ANNErrorHandler.h"
#import "ANNErrorTexts.h"
#import "ANNNavigationState.h"
#import "ANNReloadCoordinator.h"
#import "ApexNavigation+Internal.h"

// The commands that send `ANN.CommandCompleted` once they resolved: every one that shows or dismisses, not
// `setColumnShown`, `preparePush` or `getLaunchLink`.
static NSSet<NSString *> *ANNCompletedCommandNames(void) {
    static NSSet<NSString *> *names;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      names = [NSSet setWithArray:@[
          @"setRoot", @"push", @"pop", @"popTo", @"popToRoot", @"setStackRoot", @"showInColumn",
          @"showModal", @"dismissModal", @"dismissAllModals", @"showOverlay", @"dismissOverlay",
          @"dismissAllOverlays"
      ]];
    });
    return names;
}

@implementation ANNCommandAnswer {
    RCTPromiseResolveBlock _resolve;
    RCTPromiseRejectBlock _reject;
}

- (instancetype)initWithCommandName:(NSString *)commandName
                          commandId:(NSString *)commandId
                         generation:(NSUInteger)generation
                            resolve:(RCTPromiseResolveBlock)resolve
                             reject:(RCTPromiseRejectBlock)reject {
    if (self = [super init]) {
        _commandName = [commandName copy];
        _commandId = [commandId copy];
        _generation = generation;
        _resolve = resolve;
        _reject = reject;
    }
    return self;
}

// The last reference went without an answer: nothing took the command, or what took it let it go. The promise of a
// JavaScript that still runs rejects, so it never waits forever. Any thread: the promise blocks are React Native's,
// safe from any thread, and the generation is readable from any thread.
- (void)dealloc {
    if (_answered || !self.isCurrent) {
        return;
    }
    [ANNErrorHandler reject:_reject of:_commandName text:[ANNErrorTexts notAnswered:_commandName]];
}

- (BOOL)isCurrent {
    return _generation == ANNReloadCoordinator.generation;
}

// The promise of a JavaScript that went is left alone, as are the blocks once used.
- (BOOL)takeAnswer {
    ANNAssertMainQueue();
    if (_answered || !self.isCurrent) {
        return NO;
    }
    _answered = YES;
    return YES;
}

- (void)resolve:(id)value {
    if (![self takeAnswer]) {
        return;
    }
    RCTPromiseResolveBlock resolve = _resolve;
    _resolve = nil;
    _reject = nil;
    // A command that may change what shows (the 13 and `setColumnShown`): the state goes before the promise, so that
    // `getState()` after `await` has the result.
    BOOL changesWhatShows = [ANNCompletedCommandNames() containsObject:_commandName] ||
                            [_commandName isEqualToString:@"setColumnShown"];
    if (changesWhatShows) {
        // The command did its work: a failure of the state step costs only the state, never the answer.
        @try {
            [ANNNavigationState reportBeforeAnswer];
        } @catch (NSException *exception) {
            NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
            [ANNErrorHandler logFailureOf:_commandName
                                     text:[ANNErrorTexts couldNot:@"report the navigation state"
                                                            reason:reason]];
        }
    }
    [ANNErrorHandler resolve:resolve of:_commandName value:value];
    if (_commandId && [ANNCompletedCommandNames() containsObject:_commandName]) {
        double completionTime = floor(NSDate.date.timeIntervalSince1970 * 1000);
        [ApexNavigation.eventEmitter sendCommandCompleted:_commandName
                                                commandId:_commandId
                                           completionTime:completionTime];
    }
}

- (void)reject:(NSString *)text {
    if (![self takeAnswer]) {
        return;
    }
    RCTPromiseRejectBlock reject = _reject;
    _resolve = nil;
    _reject = nil;
    [ANNErrorHandler reject:reject of:_commandName text:text];
}

@end
