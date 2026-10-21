#import "ANNArrangementState.h"
#import "ANNArrangementViewController.h"

static NSMutableDictionary<NSString *, NSDictionary<NSString *, id> *> *ANNArrangementStateById;

@implementation ANNArrangementState

+ (void)initialize {
    if (self == ANNArrangementState.class) {
        ANNArrangementStateById = [NSMutableDictionary new];
    }
}

+ (BOOL)canShowArrangements {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        return ANNArrangementViewController.class != nil;
    }
#endif
    return NO;
}

+ (void)setState:(NSDictionary<NSString *, id> *)state
     forScreenId:(NSString *)screenId
    eventEmitter:(ANNEventEmitter *)eventEmitter {
    @synchronized(ANNArrangementStateById) {
        if ([ANNArrangementStateById[screenId] isEqualToDictionary:state]) {
            return;
        }
        ANNArrangementStateById[screenId] = state;
    }
    [eventEmitter sendArrangementChanged:screenId state:state];
}

+ (NSDictionary<NSString *, id> *)stateForScreenId:(NSString *)screenId {
    @synchronized(ANNArrangementStateById) {
        return ANNArrangementStateById[screenId];
    }
}

+ (void)removeScreenId:(NSString *)screenId {
    @synchronized(ANNArrangementStateById) {
        [ANNArrangementStateById removeObjectForKey:screenId];
    }
}

+ (void)removeAll {
    @synchronized(ANNArrangementStateById) {
        [ANNArrangementStateById removeAllObjects];
    }
}

@end
