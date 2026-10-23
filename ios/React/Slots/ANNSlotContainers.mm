#import "ANNSlotContainers.h"
#import "ANNAssert.h"

NSString *const ANNSlotPrefix = @"slot:";

NSString *ANNComponentNameWithoutSlotPrefix(NSString *name) {
    return [name hasPrefix:ANNSlotPrefix] ? [name substringFromIndex:ANNSlotPrefix.length] : name;
}

@implementation ANNSlotContainers

+ (NSMapTable<NSString *, UIView *> *)containers {
    static NSMapTable *containers;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      containers = [NSMapTable strongToWeakObjectsMapTable];
    });
    return containers;
}

+ (void)setContainer:(UIView *)container forSlotId:(NSString *)slotId {
    [self.containers setObject:container forKey:slotId];
    [self containerDidChange:slotId];
}

+ (UIView *)containerForSlotId:(NSString *)slotId {
    return [self.containers objectForKey:slotId];
}

+ (void)removeContainerForSlotId:(NSString *)slotId {
    [self.containers removeObjectForKey:slotId];
    [self containerDidChange:slotId];
}

// The slot views by slot id, weakly, in the order they came. Two live views under one id both stay and both hear
// the change, in that order, as every view with the id did when the change went to all of them.
+ (NSMutableDictionary<NSString *, NSPointerArray *> *)slotViews {
    static NSMutableDictionary *slotViews;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      slotViews = [NSMutableDictionary dictionary];
    });
    return slotViews;
}

// The id each slot view is under, so a view is under one id at a time.
+ (NSMapTable<id<ANNSlotView>, NSString *> *)slotIds {
    static NSMapTable *slotIds;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      slotIds = [[NSMapTable alloc]
          initWithKeyOptions:NSPointerFunctionsWeakMemory | NSPointerFunctionsObjectPointerPersonality
                valueOptions:NSPointerFunctionsStrongMemory
                    capacity:0];
    });
    return slotIds;
}

+ (void)containerDidChange:(NSString *)slotId {
    ANNAssertMainQueue();
    // A copy: a view may register or leave while it hears the change.
    for (id<ANNSlotView> view in [self slotViewsForSlotId:slotId]) {
        [view slotContainerDidChange:slotId];
    }
}

+ (void)registerSlotView:(id<ANNSlotView>)view forSlotId:(NSString *)slotId {
    ANNAssertMainQueue();
    if ([[self.slotIds objectForKey:view] isEqualToString:slotId]) {
        return;
    }
    [self unregisterSlotView:view];
    [self.slotIds setObject:slotId forKey:view];
    NSPointerArray *views = self.slotViews[slotId];
    if (views == nil) {
        views = [NSPointerArray weakObjectsPointerArray];
        self.slotViews[slotId] = views;
    }
    [views addPointer:(__bridge void *)view];
}

+ (void)unregisterSlotView:(id<ANNSlotView>)view {
    ANNAssertMainQueue();
    NSString *slotId = [self.slotIds objectForKey:view];
    if (slotId == nil) {
        return;
    }
    [self.slotIds removeObjectForKey:view];
    [self pruneSlotId:slotId dropping:view];
}

+ (NSArray<id<ANNSlotView>> *)slotViewsForSlotId:(NSString *)slotId {
    [self pruneSlotId:slotId dropping:nil];
    return self.slotViews[slotId].allObjects ?: @[];
}

// Drops the deallocated views under the id and `view`; an id with no views left goes. By hand rather than with
// `compact`, which keeps the nil entries of a weak pointer array.
+ (void)pruneSlotId:(NSString *)slotId dropping:(nullable id<ANNSlotView>)view {
    NSPointerArray *views = self.slotViews[slotId];
    for (NSInteger index = (NSInteger)views.count - 1; index >= 0; index--) {
        void *pointer = [views pointerAtIndex:(NSUInteger)index];
        if (pointer == NULL || pointer == (__bridge void *)view) {
            [views removePointerAtIndex:(NSUInteger)index];
        }
    }
    if (views != nil && views.count == 0) {
        [self.slotViews removeObjectForKey:slotId];
    }
}

@end
