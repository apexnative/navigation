#import "ANNScreenInsets.h"
#import "ANNEventContract.h"

static NSMutableDictionary<NSString *, NSDictionary<NSString *, NSNumber *> *> *ANNScreenInsetsById;
// Main queue only.
static NSMutableDictionary<NSString *, NSValue *> *ANNScreenPaddingById;
// The safe area views of each screen, by its id (the id's value: the view's id and the core's are other strings), held
// weakly. Main queue only.
static NSMutableDictionary<NSString *, NSHashTable<id<ANNScreenPaddingListener>> *> *ANNPaddingListenersById;

@implementation ANNScreenInsets

+ (void)initialize {
    if (self == ANNScreenInsets.class) {
        ANNScreenInsetsById = [NSMutableDictionary new];
        ANNScreenPaddingById = [NSMutableDictionary new];
        ANNPaddingListenersById = [NSMutableDictionary new];
    }
}

+ (void)setInsets:(UIEdgeInsets)insets
          padding:(UIEdgeInsets)padding
      forScreenId:(NSString *)screenId
     eventEmitter:(ANNEventEmitter *)eventEmitter {
    NSValue *paddingValue = [NSValue valueWithUIEdgeInsets:padding];
    if (![ANNScreenPaddingById[screenId] isEqualToValue:paddingValue]) {
        ANNScreenPaddingById[screenId] = paddingValue;
        // A copy: a listener may stop or start listening while it is told.
        for (id<ANNScreenPaddingListener> listener in ANNPaddingListenersById[screenId].allObjects) {
            [listener screenPaddingDidChange];
        }
    }
    // JavaScript gets what still covers the content: the edges the safe area view keeps it off are 0.
    const auto &keys = ANNEventContract.ScreenInsetsChanged;
    NSDictionary<NSString *, NSNumber *> *value = @{
        keys.top : @(MAX(insets.top - padding.top, 0)),
        keys.right : @(MAX(insets.right - padding.right, 0)),
        keys.bottom : @(MAX(insets.bottom - padding.bottom, 0)),
        keys.left : @(MAX(insets.left - padding.left, 0)),
    };
    @synchronized(ANNScreenInsetsById) {
        if ([ANNScreenInsetsById[screenId] isEqualToDictionary:value]) {
            return;
        }
        ANNScreenInsetsById[screenId] = value;
    }
    [eventEmitter sendScreenInsetsChanged:screenId insets:value];
}

+ (NSDictionary<NSString *, NSNumber *> *)insetsForScreenId:(NSString *)screenId {
    @synchronized(ANNScreenInsetsById) {
        return ANNScreenInsetsById[screenId];
    }
}

+ (UIEdgeInsets)paddingForScreenId:(NSString *)screenId {
    return ANNScreenPaddingById[screenId].UIEdgeInsetsValue;
}

+ (void)addPaddingListener:(id<ANNScreenPaddingListener>)listener forScreenId:(NSString *)screenId {
    NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
    if (!listeners) {
        listeners = [NSHashTable weakObjectsHashTable];
        ANNPaddingListenersById[screenId] = listeners;
    }
    [listeners addObject:listener];
}

+ (void)removePaddingListener:(id<ANNScreenPaddingListener>)listener forScreenId:(NSString *)screenId {
    NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
    [listeners removeObject:listener];
    [self forgetListenersOfScreenIdIfNone:screenId];
}

// A view that went (it doesn't remove itself in its `dealloc`) leaves no entry in the weak table: `allObjects` counts
// only the live ones. The table of a screen nobody listens to goes with the last one, or with the screen's data.
+ (void)forgetListenersOfScreenIdIfNone:(NSString *)screenId {
    NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
    if (listeners && listeners.allObjects.count == 0) {
        [ANNPaddingListenersById removeObjectForKey:screenId];
    }
}

+ (void)removeScreenId:(NSString *)screenId {
    [ANNScreenPaddingById removeObjectForKey:screenId];
    [self forgetListenersOfScreenIdIfNone:screenId];
    @synchronized(ANNScreenInsetsById) {
        [ANNScreenInsetsById removeObjectForKey:screenId];
    }
}

+ (void)removeAll {
    [ANNScreenPaddingById removeAllObjects];
    for (NSString *screenId in ANNPaddingListenersById.allKeys) {
        [self forgetListenersOfScreenIdIfNone:screenId];
    }
    @synchronized(ANNScreenInsetsById) {
        [ANNScreenInsetsById removeAllObjects];
    }
}

@end
