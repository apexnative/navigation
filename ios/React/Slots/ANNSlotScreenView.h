#import "ANNReactView.h"
#import "ANNSlotContainers.h"
#import <UIKit/UIKit.h>

@class RCTFabricSurface;
@class RCTHost;

NS_ASSUME_NONNULL_BEGIN

/// The view of a slot screen (a component named with `ANNSlotPrefix`): a screen of the app's one React tree. It has no
/// React surface of its own; the slot of the tree with the screen's componentId shows its content here. It answers
/// what a screen asks of its React view, so it stands in for one.
@interface ANNSlotScreenView : UIView <ANNComponentProtocol, ANNSlotContainer>

- (instancetype)initWithComponentId:(NSString *)componentId
                               name:(NSString *)name
                               host:(RCTHost *)host
                       eventEmitter:(ANNEventEmitter *)eventEmitter
                reactViewReadyBlock:(nullable ANNReactViewReadyCompletionBlock)reactViewReadyBlock;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

/// The screen's name, without the prefix.
@property(nonatomic, readonly) NSString *moduleName;
/// Always nil: the content belongs to the host's surface.
@property(nonatomic, readonly, nullable) RCTFabricSurface *surface;
@property(nonatomic, assign) BOOL passThroughTouches;
@property(nonatomic, strong) ANNEventEmitter *eventEmitter;

/// Called each time the slot mounts content here, one handler for each key; nil removes it.
- (void)setContentDidMount:(nullable void (^)(void))handler forKey:(NSString *)key;
/// The screen goes: its slot takes the content back and JavaScript unmounts it.
- (void)stopSurface;

@end

NS_ASSUME_NONNULL_END
