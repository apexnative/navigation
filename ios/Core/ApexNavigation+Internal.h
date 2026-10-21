#import "ANNEventEmitter.h"
#import "ApexNavigation.h"

NS_ASSUME_NONNULL_BEGIN

/// What the core keeps from the start of the navigation (`bootstrapWithHost:`), for its own parts. Main queue.
@interface ApexNavigation ()

/// The navigation's window: made at the start, before any scene, and moved into the app's scene when it connects.
@property(class, nonatomic, readonly, nullable) UIWindow *mainWindow;

/// The sender of the events of the JavaScript that runs now: a new one each time the JavaScript changed. Keep the one
/// you were made with; ask again only for the events of the navigation as a whole (the state, `AppLaunched`). Any
/// thread.
@property(class, nonatomic, readonly) ANNEventEmitter *eventEmitter;

/// The creator registered for a native screen, or nil. Any thread.
+ (nullable ANNNativeScreenCreator)nativeScreenCreatorForName:(NSString *)name;

/// Whether a native screen is registered under the name by `registerNativeScreen:creator:` (the registration of 8.x is
/// gone). Any thread: the layout of a command is checked on the module's queue.
+ (BOOL)hasNativeScreenNamed:(NSString *)name;

@end

NS_ASSUME_NONNULL_END
