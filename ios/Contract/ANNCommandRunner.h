#import "ANNCommandAnswer.h"
#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

/// Reads and checks a command's layout on the module's queue: the layout read, or nil with the text of the rejection.
typedef id _Nullable (^ANNCommandCheck)(NSString *_Nullable *_Nonnull error);

/// The command's own work on the main queue, with the layout its check read (nil without a check). It answers once,
/// now or later; an exception it throws rejects the command, and undoing what it made by then is the body's.
typedef void (^ANNCommandBody)(id _Nullable layout, ANNCommandAnswer *answer);

/// The one path of every asynchronous method of `ANNTurboModule`. On the module's queue, at once: the command takes the
/// generation of its JavaScript and its layout is read and checked whole; a failure rejects it there and nothing is
/// queued. Then one task on the main queue per command, in the order they came: the command does nothing when its
/// JavaScript went, rejects when the navigation has nowhere to show screens, and runs its body inside the protection.
@interface ANNCommandRunner : NSObject

/// A command with a promise. `what` completes the text of an unexpected failure, `could not <what>: <reason>`. A
/// command with a `commandId` needs the navigation's window (`bootstrapWithHost:`), else it rejects
/// `[ANNErrorTexts nowhereToShow]`; `getLaunchLink` has no `commandId` and doesn't need it.
+ (void)run:(NSString *)name
    commandId:(nullable NSString *)commandId
         what:(NSString *)what
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
        check:(nullable ANNCommandCheck)check
         body:(ANNCommandBody)body;

/// A command without a promise: its body on the main queue, inside the protection; a failure is one log line.
+ (void)runVoid:(NSString *)name what:(NSString *)what body:(dispatch_block_t)body;

/// The log line of a command without a promise that failed: `[ApexNavigation] <name>: <text>`.
+ (void)logFailure:(NSString *)name text:(NSString *)text;

/// The text a command rejects with for `exception`: the text of an `ANNCommandRejectionException` as it is, else
/// `could not <what>: <reason>`. The steps of a command that run after its body (a stack's queue) reject with it too.
+ (NSString *)rejectionOf:(NSException *)exception what:(NSString *)what;

@end

NS_ASSUME_NONNULL_END
