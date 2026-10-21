#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

/// How a failed command tells JavaScript: a rejection carries only a sentence, never a code of its own, and a command
/// without a promise writes the sentence to the log. The sentences are those of `ANNErrorTexts`.
@interface ANNErrorHandler : NSObject

/// Rejects a command's promise with the sentence alone: React Native puts its own default code on it.
+ (void)reject:(RCTPromiseRejectBlock)reject text:(NSString *)text;

/// The log line of a command without a promise: `[ApexNavigation] <method>: <text>`.
+ (void)logFailureOf:(NSString *)method text:(NSString *)text;

@end

NS_ASSUME_NONNULL_END
