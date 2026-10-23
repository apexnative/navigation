#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

/// How a failed command tells JavaScript: a rejection carries only a sentence, never a code of its own, and a command
/// without a promise writes the sentence to the log. The sentences are those of `ANNErrorTexts`. The one place that
/// calls a command's promise blocks: a block that is nil (a caller that passed none) is never called.
@interface ANNErrorHandler : NSObject

/// Rejects a command's promise with the sentence alone: React Native puts its own default code on it. Without a
/// reject block the sentence goes to the log as `logFailureOf:text:` writes it.
+ (void)reject:(nullable RCTPromiseRejectBlock)reject of:(NSString *)method text:(NSString *)text;

/// Resolves a command's promise with `value`. Without a resolve block one line goes to the log instead.
+ (void)resolve:(nullable RCTPromiseResolveBlock)resolve of:(NSString *)method value:(nullable id)value;

/// The log line of a command without a promise: `[ApexNavigation] <method>: <text>`, a warning (`RCTLogWarn`).
+ (void)logFailureOf:(NSString *)method text:(NSString *)text;

@end

NS_ASSUME_NONNULL_END
