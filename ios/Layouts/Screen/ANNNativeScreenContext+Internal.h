#import "ApexNavigation.h"

@class ANNEventEmitter;

NS_ASSUME_NONNULL_BEGIN

@interface ANNNativeScreenContext ()

- (instancetype)initWithComponentId:(NSString *)componentId
                              props:(nullable NSDictionary *)props
                          functions:(nullable NSArray<NSString *> *)functions
                       eventEmitter:(nullable ANNEventEmitter *)eventEmitter;

/// New props from JS, on the main thread: `{ props, removed, functions }`, the changed ones and the names of those gone.
- (void)applyUpdate:(NSDictionary *)update;

@end

NS_ASSUME_NONNULL_END
