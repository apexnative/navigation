#import "ANNCommandRunner.h"
#import "ANNErrorHandler.h"
#import "ANNErrorTexts.h"
#import "ANNReloadCoordinator.h"
#import "ApexNavigation+Internal.h"
#import <React/RCTUtils.h>

// Why an exception happened: its message, else its name.
static NSString *ANNReasonOf(NSException *exception) {
    return exception.reason.length > 0 ? exception.reason : exception.name;
}

// The text a command rejects with for an exception: a rejection's own text as it is, else `could not <what>: <why>`.
static NSString *ANNRejectionOf(NSException *exception, NSString *what) {
    if ([exception.name isEqualToString:ANNCommandRejectionException] && exception.reason.length > 0) {
        return exception.reason;
    }
    return [ANNErrorTexts couldNot:what reason:ANNReasonOf(exception)];
}

@implementation ANNCommandRunner

+ (void)run:(NSString *)name
    commandId:(NSString *)commandId
         what:(NSString *)what
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
        check:(ANNCommandCheck)check
         body:(ANNCommandBody)body {
    NSUInteger generation = ANNReloadCoordinator.generation;
    id layout = nil;
    if (check) {
        NSString *error = nil;
        @try {
            layout = check(&error);
        } @catch (NSException *exception) {
            error = ANNRejectionOf(exception, what);
        }
        if (error) {
            [ANNErrorHandler reject:reject text:error];
            return;
        }
    }
    ANNCommandAnswer *answer = [[ANNCommandAnswer alloc] initWithCommandName:name
                                                                   commandId:commandId
                                                                  generation:generation
                                                                     resolve:resolve
                                                                      reject:reject];
    // At once when the caller is on the main queue already; the module's queue never is.
    RCTExecuteOnMainQueue(^{
      if (!answer.isCurrent) {
          return;
      }
      if (commandId && !ApexNavigation.mainWindow) {
          [answer reject:ANNErrorTexts.nowhereToShow];
          return;
      }
      @try {
          body(layout, answer);
      } @catch (NSException *exception) {
          [answer reject:ANNRejectionOf(exception, what)];
      }
    });
}

// Before the navigation started a command without a promise has nothing to act on: its body finds nothing.
+ (void)runVoid:(NSString *)name what:(NSString *)what body:(dispatch_block_t)body {
    NSUInteger generation = ANNReloadCoordinator.generation;
    RCTExecuteOnMainQueue(^{
      if (generation != ANNReloadCoordinator.generation) {
          return;
      }
      @try {
          body();
      } @catch (NSException *exception) {
          [self logFailure:name text:[ANNErrorTexts couldNot:what reason:ANNReasonOf(exception)]];
      }
    });
}

+ (void)logFailure:(NSString *)name text:(NSString *)text {
    [ANNErrorHandler logFailureOf:name text:text];
}

+ (NSString *)rejectionOf:(NSException *)exception what:(NSString *)what {
    return ANNRejectionOf(exception, what);
}

@end
