#import "ANNScreenEnvironment.h"
#import "ANNEventContract.h"

// One screen's record; nil for a fact not known yet (not laid out, no regions, outside a pair). Changed and read only
// under the lock of `ANNEnvironmentById`; the values are immutable, so a reader keeps what it took.
@interface ANNScreenEnvironmentRecord : NSObject
@property(nonatomic, copy, nullable) NSDictionary<NSString *, NSNumber *> *insets;
@property(nonatomic, strong, nullable) NSValue *padding;
@property(nonatomic, copy, nullable) NSDictionary<NSString *, NSArray *> *regions;
@property(nonatomic, copy, nullable) NSDictionary<NSString *, id> *arrangement;
@end

@implementation ANNScreenEnvironmentRecord
@end

// Written on the main queue, read from any thread: every access holds its lock.
static NSMutableDictionary<NSString *, ANNScreenEnvironmentRecord *> *ANNEnvironmentById;
// The safe area views of each screen, by its id (the id's value: the view's id and the core's are other strings), held
// weakly. Main queue only.
static NSMutableDictionary<NSString *, NSHashTable<id<ANNScreenPaddingListener>> *>
    *ANNPaddingListenersById;

@implementation ANNScreenEnvironment

+ (void)initialize {
    if (self == ANNScreenEnvironment.class) {
        ANNEnvironmentById = [NSMutableDictionary new];
        ANNPaddingListenersById = [NSMutableDictionary new];
    }
}

// The screen's record, a new one for a screen not kept yet. Under the lock.
+ (ANNScreenEnvironmentRecord *)recordOf:(NSString *)screenId {
    ANNScreenEnvironmentRecord *record = ANNEnvironmentById[screenId];
    if (!record) {
        record = [ANNScreenEnvironmentRecord new];
        ANNEnvironmentById[screenId] = record;
    }
    return record;
}

#pragma mark - Insets and padding

+ (void)setInsets:(UIEdgeInsets)insets
          padding:(UIEdgeInsets)padding
      forScreenId:(NSString *)screenId
     eventEmitter:(ANNEventEmitter *)eventEmitter {
    NSValue *paddingValue = [NSValue valueWithUIEdgeInsets:padding];
    // JavaScript gets what still covers the content: the edges the safe area view keeps it off are 0.
    const auto &keys = ANNEventContract.ScreenInsetsChanged;
    NSDictionary<NSString *, NSNumber *> *value = @{
        keys.top : @(MAX(insets.top - padding.top, 0)),
        keys.right : @(MAX(insets.right - padding.right, 0)),
        keys.bottom : @(MAX(insets.bottom - padding.bottom, 0)),
        keys.left : @(MAX(insets.left - padding.left, 0)),
    };
    BOOL paddingChanged;
    BOOL insetsChanged;
    @synchronized(ANNEnvironmentById) {
        ANNScreenEnvironmentRecord *record = [self recordOf:screenId];
        paddingChanged = ![record.padding isEqualToValue:paddingValue];
        insetsChanged = ![record.insets isEqualToDictionary:value];
        record.padding = paddingValue;
        record.insets = value;
    }
    // The listeners read the padding back, so it is kept before they are told. A copy: a listener may stop or start
    // listening while it is told.
    if (paddingChanged) {
        NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
        for (id<ANNScreenPaddingListener> listener in listeners.allObjects) {
            [listener screenPaddingDidChange];
        }
    }
    if (insetsChanged) {
        [eventEmitter sendScreenInsetsChanged:screenId insets:value];
    }
}

+ (UIEdgeInsets)paddingForScreenId:(NSString *)screenId {
    @synchronized(ANNEnvironmentById) {
        return ANNEnvironmentById[screenId].padding.UIEdgeInsetsValue;
    }
}

+ (NSDictionary<NSString *, NSNumber *> *)insetsForScreenId:(NSString *)screenId {
    @synchronized(ANNEnvironmentById) {
        return ANNEnvironmentById[screenId].insets;
    }
}

#pragma mark - Regions

+ (void)setRegions:(NSDictionary<NSString *, NSArray *> *)regions
       forScreenId:(NSString *)screenId
      eventEmitter:(ANNEventEmitter *)eventEmitter {
    const auto &keys = ANNEventContract.ScreenRegionsChanged;
    BOOL empty = [regions[keys.division] count] == 0 && [regions[keys.occlusion] count] == 0;
    @synchronized(ANNEnvironmentById) {
        NSDictionary<NSString *, NSArray *> *before = ANNEnvironmentById[screenId].regions;
        if ((!before && empty) || [before isEqualToDictionary:regions]) {
            return;
        }
        [self recordOf:screenId].regions = regions;
    }
    [eventEmitter sendScreenRegionsChanged:screenId regions:regions];
}

+ (NSDictionary<NSString *, NSArray *> *)regionsForScreenId:(NSString *)screenId {
    @synchronized(ANNEnvironmentById) {
        return ANNEnvironmentById[screenId].regions;
    }
}

#pragma mark - Arrangement

+ (void)setArrangement:(NSDictionary<NSString *, id> *)arrangement
           forScreenId:(NSString *)screenId
          eventEmitter:(ANNEventEmitter *)eventEmitter {
    @synchronized(ANNEnvironmentById) {
        ANNScreenEnvironmentRecord *record = [self recordOf:screenId];
        if ([record.arrangement isEqualToDictionary:arrangement]) {
            return;
        }
        record.arrangement = arrangement;
    }
    [eventEmitter sendArrangementChanged:screenId state:arrangement];
}

+ (NSDictionary<NSString *, id> *)arrangementForScreenId:(NSString *)screenId {
    @synchronized(ANNEnvironmentById) {
        return ANNEnvironmentById[screenId].arrangement;
    }
}

#pragma mark - Padding listeners

+ (void)addPaddingListener:(id<ANNScreenPaddingListener>)listener
               forScreenId:(NSString *)screenId {
    NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
    if (!listeners) {
        listeners = [NSHashTable weakObjectsHashTable];
        ANNPaddingListenersById[screenId] = listeners;
    }
    [listeners addObject:listener];
}

+ (void)removePaddingListener:(id<ANNScreenPaddingListener>)listener
                  forScreenId:(NSString *)screenId {
    NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
    [listeners removeObject:listener];
    [self forgetListenersOfScreenIdIfNone:screenId];
}

// A view that went (it doesn't remove itself in its `dealloc`) leaves no entry in the weak table: `allObjects` counts
// only the live ones. The table of a screen nobody listens to goes with the last one, or with the screen's record.
+ (void)forgetListenersOfScreenIdIfNone:(NSString *)screenId {
    NSHashTable<id<ANNScreenPaddingListener>> *listeners = ANNPaddingListenersById[screenId];
    if (listeners && listeners.allObjects.count == 0) {
        [ANNPaddingListenersById removeObjectForKey:screenId];
    }
}

#pragma mark - Removal

+ (void)removeScreenId:(NSString *)screenId {
    @synchronized(ANNEnvironmentById) {
        [ANNEnvironmentById removeObjectForKey:screenId];
    }
    [self forgetListenersOfScreenIdIfNone:screenId];
}

+ (void)removeAll {
    @synchronized(ANNEnvironmentById) {
        [ANNEnvironmentById removeAllObjects];
    }
    for (NSString *screenId in ANNPaddingListenersById.allKeys) {
        [self forgetListenersOfScreenIdIfNone:screenId];
    }
}

@end
