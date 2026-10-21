#import "ANNComponentViewCreator.h"
#import "ANNEventEmitter.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The core's maker of the content of screens: a name with `ANNSlotPrefix` gets the slot screen view of the app's one
/// React tree (`ANNSlotScreenView`), any other name a React view with a surface of its own from the app's host
/// (`ApexNavigation.getHost`). The layout factory makes one per command, with the emitter of the nodes it makes, so
/// that the content and its node tell the same JavaScript. Main queue.
@interface ANNReactViewCreator : NSObject <ANNComponentViewCreator>

- (instancetype)initWithEventEmitter:(ANNEventEmitter *)eventEmitter NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
