#import "ANNElementTransition.h"
#import "ANNElementFinder.h"
#import "ANNElementTransitionOptions.h"
#import "ANNViewTracks.h"

@implementation ANNElementTransition {
    ANNViewTracks *_tracks;
}

- (instancetype)initWithTracks:(ANNViewTracks *)tracks {
    if (self = [super init]) {
        _tracks = tracks;
    }
    return self;
}

+ (UIView *)viewOf:(ANNElementTransitionOptions *)element
       leavingRoot:(UIView *)leavingRoot
      arrivingRoot:(UIView *)arrivingRoot {
    NSString *identifier = [element.identifier withDefault:nil];
    if (![identifier isKindOfClass:NSString.class] || identifier.length == 0) {
        return nil;
    }
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
        UIView *view = [self viewOf:element leavingRoot:leavingRoot arrivingRoot:arrivingRoot];
        if (!view || [taken containsObject:view]) {
            continue;
        }
        ANNViewTracks *tracks = [[ANNViewTracks alloc] initWithView:view element:element];
        if (!tracks.hasTracks) {
            continue;
        }
        [taken addObject:view];
        [transitions addObject:[[ANNElementTransition alloc] initWithTracks:tracks]];
    }
    return transitions;
}

+ (BOOL)allFound:(NSArray<ANNElementTransitionOptions *> *)elements
     leavingRoot:(UIView *)leavingRoot
    arrivingRoot:(UIView *)arrivingRoot {
    for (ANNElementTransitionOptions *element in elements) {
        if (![self viewOf:element leavingRoot:leavingRoot arrivingRoot:arrivingRoot]) {
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
