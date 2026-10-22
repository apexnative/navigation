#import "ANNElementTransition.h"
#import "ANNElementFinder.h"
#import "ANNElementTransitionOptions.h"
#import "ANNViewTracks.h"

@implementation ANNElementTransition {
    ANNViewTracks *_tracks;
}

- (instancetype)initWithTracks:(ANNViewTracks *)tracks name:(NSString *)name {
    if (self = [super init]) {
        _tracks = tracks;
        _name = [name copy];
    }
    return self;
}

+ (UIView *)viewOf:(NSString *)identifier views:(ANNTransitionViews *)views {
    return [views leavingViewWithNativeID:identifier]
               ?: [views arrivingViewWithNativeID:identifier];
}

+ (NSArray<ANNElementTransition *> *)
    transitionsOf:(NSArray<ANNElementTransitionOptions *> *)elements
            views:(ANNTransitionViews *)views {
    NSMutableArray<ANNElementTransition *> *transitions = [NSMutableArray new];
    // One view takes one item: a second item for the same view would read the values the first one writes.
    NSHashTable<UIView *> *taken = [NSHashTable weakObjectsHashTable];
    for (ANNElementTransitionOptions *element in elements) {
        NSString *identifier = ANNNativeIDOf(element.identifier);
        UIView *view = [self viewOf:identifier views:views];
        if (!view || [taken containsObject:view]) {
            continue;
        }
        NSString *name = [NSString stringWithFormat:@"'%@'", identifier];
        __block ANNViewTracks *tracks = nil;
        if (!ANNElementStep(name, ^{
              tracks = [[ANNViewTracks alloc] initWithView:view element:element];
            }) ||
            !tracks.hasTracks) {
            continue;
        }
        [taken addObject:view];
        [transitions addObject:[[ANNElementTransition alloc] initWithTracks:tracks name:name]];
    }
    return transitions;
}

+ (BOOL)allFound:(NSArray<ANNElementTransitionOptions *> *)elements
           views:(ANNTransitionViews *)views {
    for (ANNElementTransitionOptions *element in elements) {
        if (![self viewOf:ANNNativeIDOf(element.identifier) views:views]) {
            return NO;
        }
    }
    return YES;
}

#pragma mark - ANNFrameClient

- (void)applyAtTime:(NSTimeInterval)time {
    [_tracks applyAtTime:time];
}

- (NSTimeInterval)endTime {
    return _tracks.endTime;
}

- (void)restore {
    [_tracks restore];
}

@end
