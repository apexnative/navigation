#import "ANNScreenReadiness.h"
#import "ANNAssert.h"

// What the safe area view of one id said.
@interface ANNScreenReadinessEntry : NSObject
@property(nonatomic) BOOL hasSafeAreaView;
@property(nonatomic) BOOL laidOut;
@property(nonatomic) BOOL rendered;
@end

@implementation ANNScreenReadinessEntry
@end

// Main queue.
static NSMutableDictionary<NSString *, ANNScreenReadinessEntry *> *ANNReadinessEntries;
static NSMapTable<NSString *, id<ANNScreenReadinessListener>> *ANNReadinessListeners;
static NSMutableSet<NSString *> *ANNRendersAtOnce;
// The live listeners of the ids that render at once, each id with its own weak list.
static NSMutableDictionary<NSString *, NSHashTable<id<ANNScreenReadinessListener>> *>
    *ANNSharedListeners;

@implementation ANNScreenReadiness

+ (void)initialize {
    if (self == ANNScreenReadiness.class) {
        ANNReadinessEntries = [NSMutableDictionary new];
        ANNReadinessListeners = [NSMapTable strongToWeakObjectsMapTable];
        ANNRendersAtOnce = [NSMutableSet new];
        ANNSharedListeners = [NSMutableDictionary new];
    }
}

// Every live listener of the id: the shared ones of an id that renders at once, else the one.
+ (NSArray<id<ANNScreenReadinessListener>> *)listenersOf:(NSString *)screenId {
    NSHashTable<id<ANNScreenReadinessListener>> *shared = ANNSharedListeners[screenId];
    if (shared) {
        return shared.allObjects;
    }
    id<ANNScreenReadinessListener> listener = [ANNReadinessListeners objectForKey:screenId];
    return listener ? @[ listener ] : @[];
}

+ (ANNScreenReadinessEntry *)entryOf:(NSString *)screenId {
    ANNScreenReadinessEntry *entry = ANNReadinessEntries[screenId];
    if (!entry) {
        entry = [ANNScreenReadinessEntry new];
        ANNReadinessEntries[screenId] = entry;
    }
    return entry;
}

#pragma mark - What the safe area view asks and says

+ (BOOL)screenWaitsForReadiness:(NSString *)screenId {
    ANNAssertMainQueue();
    return ![ANNRendersAtOnce containsObject:screenId];
}

+ (void)screenHasSafeAreaView:(NSString *)screenId {
    ANNAssertMainQueue();
    [self entryOf:screenId].hasSafeAreaView = YES;
}

+ (void)screenDidLayOut:(NSString *)screenId {
    ANNAssertMainQueue();
    ANNScreenReadinessEntry *entry = [self entryOf:screenId];
    entry.hasSafeAreaView = YES;
    if (entry.laidOut) {
        return;
    }
    entry.laidOut = YES;
    for (id<ANNScreenReadinessListener> listener in [self listenersOf:screenId]) {
        if ([listener respondsToSelector:@selector(screenReadinessDidLayOut:)]) {
            [listener screenReadinessDidLayOut:screenId];
        }
    }
}

+ (void)screenContentDidRender:(NSString *)screenId {
    ANNAssertMainQueue();
    ANNScreenReadinessEntry *entry = [self entryOf:screenId];
    entry.hasSafeAreaView = YES;
    // Several roots of one bar component's id each render once: every signal reaches every root of the id.
    BOOL shared = ANNSharedListeners[screenId] != nil;
    if (entry.rendered && !shared) {
        return;
    }
    entry.rendered = YES;
    for (id<ANNScreenReadinessListener> listener in [self listenersOf:screenId]) {
        if ([listener respondsToSelector:@selector(screenReadinessContentDidRender:)]) {
            [listener screenReadinessContentDidRender:screenId];
        }
    }
}

#pragma mark - What the core asks and marks

+ (void)setRendersAtOnce:(BOOL)rendersAtOnce forScreenId:(NSString *)screenId {
    ANNAssertMainQueue();
    if (rendersAtOnce) {
        [ANNRendersAtOnce addObject:screenId];
    } else {
        [ANNRendersAtOnce removeObject:screenId];
    }
}

+ (BOOL)isLaidOut:(NSString *)screenId {
    ANNAssertMainQueue();
    return ANNReadinessEntries[screenId].laidOut;
}

+ (ANNContentRendered)contentRenderedOfScreen:(NSString *)screenId {
    ANNAssertMainQueue();
    ANNScreenReadinessEntry *entry = ANNReadinessEntries[screenId];
    if (entry.rendered) {
        return ANNContentRenderedYes;
    }
    return entry.hasSafeAreaView ? ANNContentRenderedNo : ANNContentRenderedUnknown;
}

+ (void)setListener:(id<ANNScreenReadinessListener>)listener forScreenId:(NSString *)screenId {
    ANNAssertMainQueue();
    if ([ANNRendersAtOnce containsObject:screenId]) {
        NSHashTable<id<ANNScreenReadinessListener>> *shared = ANNSharedListeners[screenId];
        if (!shared) {
            shared = [NSHashTable weakObjectsHashTable];
            ANNSharedListeners[screenId] = shared;
        }
        BOOL alone = shared.allObjects.count == 0;
        [shared addObject:listener];
        // What was said belongs to another live root of the id: this one waits for its own render.
        if (!alone) {
            return;
        }
    } else {
        [ANNReadinessListeners setObject:listener forKey:screenId];
    }
    // The signals are sticky: what was said before the listener came reaches it now, in order.
    ANNScreenReadinessEntry *entry = ANNReadinessEntries[screenId];
    if (entry.laidOut && [listener respondsToSelector:@selector(screenReadinessDidLayOut:)]) {
        [listener screenReadinessDidLayOut:screenId];
    }
    if (entry.rendered && [listener respondsToSelector:@selector(screenReadinessContentDidRender:)]) {
        [listener screenReadinessContentDidRender:screenId];
    }
}

+ (void)removeListener:(id<ANNScreenReadinessListener>)listener forScreenId:(NSString *)screenId {
    ANNAssertMainQueue();
    if ([ANNReadinessListeners objectForKey:screenId] == listener) {
        [ANNReadinessListeners removeObjectForKey:screenId];
    }
    NSHashTable<id<ANNScreenReadinessListener>> *shared = ANNSharedListeners[screenId];
    if (shared) {
        [shared removeObject:listener];
        if (shared.allObjects.count == 0) {
            [ANNSharedListeners removeObjectForKey:screenId];
        }
    }
}

+ (BOOL)isListener:(id<ANNScreenReadinessListener>)listener forScreenId:(NSString *)screenId {
    ANNAssertMainQueue();
    return [ANNReadinessListeners objectForKey:screenId] == listener ||
           [ANNSharedListeners[screenId] containsObject:listener];
}

+ (NSUInteger)listenerCountForScreenId:(NSString *)screenId {
    ANNAssertMainQueue();
    return [self listenersOf:screenId].count;
}

+ (void)removeScreenId:(NSString *)screenId {
    ANNAssertMainQueue();
    [ANNReadinessEntries removeObjectForKey:screenId];
}

+ (void)reset {
    ANNAssertMainQueue();
    [ANNReadinessEntries removeAllObjects];
    [ANNReadinessListeners removeAllObjects];
    [ANNRendersAtOnce removeAllObjects];
    [ANNSharedListeners removeAllObjects];
}

@end
