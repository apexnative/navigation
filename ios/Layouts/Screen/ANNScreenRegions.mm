#import "ANNScreenRegions.h"

static NSMutableDictionary<NSString *, NSDictionary<NSString *, NSArray *> *> *ANNScreenRegionsById;

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
// The part of a region inside the screen, CGRectNull for a region outside it (the fold beside a screen of a pair, the
// camera above a screen under the bar). A fold is 0 wide or 0 high: it is inside where its line crosses the screen,
// its edges included; a region with an area that only touches the screen's edge is not.
static CGRect ANNRegionInside(CGRect region, CGRect bounds) {
    CGFloat minX = MAX(CGRectGetMinX(region), CGRectGetMinX(bounds));
    CGFloat maxX = MIN(CGRectGetMaxX(region), CGRectGetMaxX(bounds));
    CGFloat minY = MAX(CGRectGetMinY(region), CGRectGetMinY(bounds));
    CGFloat maxY = MIN(CGRectGetMaxY(region), CGRectGetMaxY(bounds));
    if (maxX < minX || maxY < minY || (maxX == minX && region.size.width > 0) ||
        (maxY == minY && region.size.height > 0)) {
        return CGRectNull;
    }
    return CGRectMake(minX, minY, maxX - minX, maxY - minY);
}

// The region itself: UIKit's frame includes the margins it keeps around it for interactive content (20 pt on each side
// of the fold of iPhone Duo, which is 0 wide without them). UIKit gives the active regions without the option to
// include the others; a region that is not active (the fold of a flat device, a camera that does not run) is left out
// all the same. Only the part inside `coordinateView` counts, as on Android.
static NSArray<NSDictionary<NSString *, NSNumber *> *> *
ANNRegionRects(NSArray<UIViewReservedRegion *> *regions, UIView *view, UIView *coordinateView)
    API_AVAILABLE(ios(27.1)) {
    NSMutableArray<NSDictionary<NSString *, NSNumber *> *> *rects = [NSMutableArray new];
    for (UIViewReservedRegion *region in regions) {
        if (!region.isActive) {
            continue;
        }
        CGRect inView = [view convertRect:UIEdgeInsetsInsetRect(region.frame, region.margins)
                                   toView:coordinateView];
        CGRect frame = ANNRegionInside(inView, coordinateView.bounds);
        if (CGRectIsNull(frame)) {
            continue;
        }
        [rects addObject:@{
            @"x" : @(frame.origin.x),
            @"y" : @(frame.origin.y),
            @"width" : @(frame.size.width),
            @"height" : @(frame.size.height),
        }];
    }
    return rects;
}
#endif

@implementation ANNScreenRegions

+ (void)initialize {
    if (self == ANNScreenRegions.class) {
        ANNScreenRegionsById = [NSMutableDictionary new];
    }
}

+ (NSDictionary<NSString *, NSArray *> *)regionsOfView:(UIView *)view
                                       inCoordinatesOf:(UIView *)coordinateView {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        NSArray<UIViewReservedRegion *> *division =
            [view reservedRegionsOfKind:[UIViewReservedRegionKind divisionRegionKind]];
        NSArray<UIViewReservedRegion *> *occlusion =
            [view reservedRegionsOfKind:[UIViewReservedRegionKind occlusionRegionKind]];
        return @{
            @"division" : ANNRegionRects(division, view, coordinateView),
            @"occlusion" : ANNRegionRects(occlusion, view, coordinateView),
        };
    }
#endif
    return nil;
}

+ (void)setRegions:(NSDictionary<NSString *, NSArray *> *)regions
       forScreenId:(NSString *)screenId
      eventEmitter:(ANNEventEmitter *)eventEmitter {
    @synchronized(ANNScreenRegionsById) {
        if ([ANNScreenRegionsById[screenId] isEqualToDictionary:regions]) {
            return;
        }
        ANNScreenRegionsById[screenId] = regions;
    }
    [eventEmitter sendScreenRegionsChanged:screenId regions:regions];
}

+ (NSDictionary<NSString *, NSArray *> *)regionsForScreenId:(NSString *)screenId {
    @synchronized(ANNScreenRegionsById) {
        return ANNScreenRegionsById[screenId];
    }
}

+ (void)removeScreenId:(NSString *)screenId {
    @synchronized(ANNScreenRegionsById) {
        [ANNScreenRegionsById removeObjectForKey:screenId];
    }
}

+ (void)removeAll {
    @synchronized(ANNScreenRegionsById) {
        [ANNScreenRegionsById removeAllObjects];
    }
}

@end
