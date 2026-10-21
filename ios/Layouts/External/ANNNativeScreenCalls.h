#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// What a call of a native screen gets: what JS returned, or the error.
typedef void (^ANNNativeScreenCallCompletion)(id _Nullable_result value, NSError *_Nullable error);

/// The calls of native screens waiting for JS to answer, by their ids. Main thread only.
@interface ANNNativeScreenCalls : NSObject

/// Keeps the completion of the call `name` (a function or a command) of the screen; returns the id JS answers it with.
+ (NSString *)addCall:(NSString *)name
             ofScreen:(NSString *)componentId
           completion:(nullable ANNNativeScreenCallCompletion)completion;

/// JS answered: `{ value }`, or `{ error }` with its message.
+ (void)resolveCall:(NSString *)callId result:(NSDictionary *)result;

+ (void)failCall:(NSString *)callId reason:(NSString *)reason;

/// The JavaScript that would answer is gone (a reload): every call fails, each with the text `reason` gives for its
/// name and its screen.
+ (void)failAllCalls:(NSString * (^)(NSString *name, NSString *componentId))reason;

@end

NS_ASSUME_NONNULL_END
