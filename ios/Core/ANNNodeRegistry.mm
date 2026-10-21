#import "ANNNodeRegistry.h"
#import "ANNAssert.h"

static NSMapTable<NSString *, UIViewController *> *ANNNodesById;

@implementation ANNNodeRegistry

+ (void)initialize {
    if (self == ANNNodeRegistry.class) {
        ANNNodesById = [NSMapTable strongToWeakObjectsMapTable];
    }
}

+ (void)registerNode:(UIViewController *)node componentId:(NSString *)componentId {
    ANNAssertMainQueue();
    [ANNNodesById setObject:node forKey:componentId];
}

+ (void)unregisterNode:(UIViewController *)node componentId:(NSString *)componentId {
    ANNAssertMainQueue();
    if ([ANNNodesById objectForKey:componentId] == node) {
        [ANNNodesById removeObjectForKey:componentId];
    }
}

+ (UIViewController *)nodeForComponentId:(NSString *)componentId {
    ANNAssertMainQueue();
    return [ANNNodesById objectForKey:componentId];
}

+ (NSArray<UIViewController *> *)allNodes {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *nodes = [NSMutableArray new];
    for (NSString *componentId in ANNNodesById.keyEnumerator.allObjects) {
        UIViewController *node = [ANNNodesById objectForKey:componentId];
        if (node) {
            [nodes addObject:node];
        }
    }
    return [nodes copy];
}

+ (void)clear {
    ANNAssertMainQueue();
    [ANNNodesById removeAllObjects];
}

@end
