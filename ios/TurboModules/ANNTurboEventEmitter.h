#import <ApexNavigationSpec/ApexNavigationSpec.h>
#import <React/RCTEventEmitter.h>

NS_ASSUME_NONNULL_BEGIN

/// The module the events `ANN.<Name>` reach JavaScript through on iOS (`NativeANNTurboEventEmitter`): React Native
/// lets through only the names of `supportedEvents`. The core sends with `ANNEventEmitter`, which goes through the
/// module of the JavaScript that runs now.
///
/// Its methods run on the main queue, where the events are sent, so the listeners JavaScript adds and removes and the
/// events never race. Without a listener an event is dropped silently.
@interface ANNTurboEventEmitter : RCTEventEmitter <NativeANNTurboEventEmitterSpec>

/// The module of the current JavaScript; nil before it started, and after it went.
@property(class, nonatomic, readonly, nullable) ANNTurboEventEmitter *current;

/// Whether JavaScript listens to any event of the module. Main queue.
@property(nonatomic, readonly) BOOL hasListeners;

/// Whether JavaScript added a listener of `ANN.AppLaunched` since it started listening. Main queue.
@property(nonatomic, readonly) BOOL listensToAppLaunched;

/// How many bundles had run when React Native made the module (its JavaScript's bundle runs then).
@property(nonatomic, readonly) NSUInteger bundlesBefore;

/// Sends the event while JavaScript listens; drops it otherwise, without React Native's warning. Main queue.
- (void)sendIfListened:(NSString *)eventName body:(nullable id)body;

@end

NS_ASSUME_NONNULL_END
