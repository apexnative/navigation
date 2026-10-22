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

+ (UIView *)viewOf:(NSString *)identifier
       leavingRoot:(UIView *)leavingRoot
      arrivingRoot:(UIView *)arrivingRoot {
    return [ANNElementFinder viewWithNativeID:identifier inView:leavingRoot]
               ?: [ANNElementFinder viewWithNativeID:identifier inView:arrivingRoot];
}

+ (NSArray<ANNElementTransition *> *)
    transitionsOf:(NSArray<ANNElementTransitionOptions *> *)elements
      leavingRoot:(UIView *)leavingRoot
     arrivingRoot:(UIView *)arrivingRoot {
    NSMutableArray<ANNElementTransition *> *transitions = [NSMutableArray new];
    // One view takes one item: a second item for the same view would read the values the first one writes.
    NSHashTable<UIView *> *taken = [NSHashTable weakObjectsHashTable];
    for (ANNElementTransitionOptions *element in elements) {
        NSString *identifier = ANNNativeIDOf(element.identifier);
        UIView *view = [self viewOf:identifier leavingRoot:leavingRoot arrivingRoot:arrivingRoot];
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
     leavingRoot:(UIView *)leavingRoot
    arrivingRoot:(UIView *)arrivingRoot {
    for (ANNElementTransitionOptions *element in elements) {
        if (![self viewOf:ANNNativeIDOf(element.identifier)
                 leavingRoot:leavingRoot
                arrivingRoot:arrivingRoot]) {
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
