#import <UIKit/UIKit.h>

@class RCTFabricSurface;
@class RCTHost;

NS_ASSUME_NONNULL_BEGIN

/// The hidden host of the app's React tree: one React surface in a hidden window of its own, so the app's providers
/// live as long as the app does and no navigation command touches them. It starts with the first slot screen, when
/// JavaScript runs; React Native starts its surface again after a reload.
@interface ANNSlotHost : NSObject

/// The name of the host's component in JavaScript.
@property(class, nonatomic, readonly) NSString *moduleName;

/// The root tag of the host's React surface; 0 before it starts.
@property(class, nonatomic, readonly) NSInteger rootTag;

/// The host's React surface; nil before it starts.
@property(class, nonatomic, readonly, nullable) RCTFabricSurface *surface;

+ (void)startWithHost:(RCTHost *)host;

/// The navigation window joined its scene: the host's window, made before any scene, goes into it too. Main queue.
+ (void)mainWindowSceneDidConnect:(UIWindowScene *)windowScene;

@end

NS_ASSUME_NONNULL_END
