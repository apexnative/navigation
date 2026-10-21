#import "ANNSlotContainers.h"

NSNotificationName const ANNSlotContainerDidChangeNotification = @"ANNSlotContainerDidChange";
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

+ (void)containerDidChange:(NSString *)slotId {
    [NSNotificationCenter.defaultCenter postNotificationName:ANNSlotContainerDidChangeNotification object:slotId];
}

@end
