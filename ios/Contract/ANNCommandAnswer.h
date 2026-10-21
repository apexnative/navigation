#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

/// The one answer of a command with a promise: the first `resolve:` or `reject:` answers, any later one does nothing,
/// and nothing answers once the JavaScript that sent the command went. `ANNCommandRunner` makes it. Main queue.
@interface ANNCommandAnswer : NSObject

- (instancetype)initWithCommandName:(NSString *)commandName
                          commandId:(nullable NSString *)commandId
                         generation:(NSUInteger)generation
                            resolve:(RCTPromiseResolveBlock)resolve
                             reject:(RCTPromiseRejectBlock)reject NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The method of `ANNTurboModule`: `push`, `setColumnShown`, …
@property(nonatomic, copy, readonly) NSString *commandName;
/// The id JS gave the call (`push13`); `preparePush` has the id of its push, `getLaunchLink` none.
@property(nonatomic, copy, readonly, nullable) NSString *commandId;
/// The generation of the JavaScript the command came from (`ANNReloadCoordinator`).
@property(nonatomic, readonly) NSUInteger generation;
/// Whether the command answered already.
@property(nonatomic, readonly) BOOL answered;
/// Whether the JavaScript the command came from still runs: its work goes on only then.
@property(nonatomic, readonly) BOOL isCurrent;

/// Resolves the promise: first the state goes, then the promise, then `ANN.CommandCompleted` for a command that shows
/// or dismisses.
- (void)resolve:(nullable id)value;

/// Rejects the promise with the text alone: no code of the navigation.
- (void)reject:(NSString *)text;

@end

NS_ASSUME_NONNULL_END
