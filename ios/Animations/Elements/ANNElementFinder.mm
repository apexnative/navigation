#import "ANNElementFinder.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNElementTransitionOptions.h"
#import "ANNSharedElementTransitionOptions.h"
#import "Text.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>
#import <React/RCTViewComponentView.h>

static NSString *ANNNativeIDOfView(UIView *view) {
    return [view isKindOfClass:RCTViewComponentView.class]
               ? ((RCTViewComponentView *)view).nativeId
               : nil;
}

// Depth first, as the search by one id: the first view of the walk with an id keeps it. NO once every id is found.
static BOOL ANNCollectViews(UIView *view, NSSet<NSString *> *nativeIDs,
                            NSMutableDictionary<NSString *, UIView *> *found) {
    NSString *nativeID = ANNNativeIDOfView(view);
    if (nativeID && !found[nativeID] && [nativeIDs containsObject:nativeID]) {
        found[nativeID] = view;
        if (found.count == nativeIDs.count) {
            return NO;
        }
    }
    for (UIView *subview in view.subviews) {
        if (!ANNCollectViews(subview, nativeIDs, found)) {
            return NO;
        }
    }
    return YES;
}

NSString *ANNNativeIDOf(Text *value) {
    NSString *text = [value withDefault:nil];
    return [text isKindOfClass:NSString.class] && text.length > 0 ? text : nil;
}

void ANNElementSkipped(NSString *element, NSString *reason) {
    RCTLogWarn(@"[ApexNavigation] the transition's element %@ is skipped, it shows and hides with "
               @"its screen while the rest plays: %@",
               element, reason);
}

BOOL ANNElementStep(NSString *element, NS_NOESCAPE dispatch_block_t work) {
    @try {
        work();
        return YES;
    } @catch (NSException *exception) {
        ANNElementSkipped(element, [NSString stringWithFormat:@"%@ (%@)", exception.reason ?: @"",
                                                              exception.name]);
        return NO;
    }
}

@implementation ANNElementFinder

+ (UIView *)searchRootOf:(UIViewController *)node {
    if (!node) {
        return nil;
    }
    UIViewController *screen = [ANNAppearanceDispatcher visibleScreenOf:node];
    return screen.reactView;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID inView:(UIView *)root {
    if (nativeID.length == 0 || !root) {
        return nil;
    }
    if ([ANNNativeIDOfView(root) isEqualToString:nativeID]) {
        return root;
    }
    for (UIView *subview in root.subviews) {
        UIView *found = [self viewWithNativeID:nativeID inView:subview];
        if (found) {
            return found;
        }
    }
    return nil;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID inNode:(UIViewController *)node {
    return [self viewWithNativeID:nativeID inView:[self searchRootOf:node]];
}

+ (NSDictionary<NSString *, UIView *> *)viewsWithNativeIDs:(NSSet<NSString *> *)nativeIDs
                                                    inView:(UIView *)root {
    NSMutableDictionary<NSString *, UIView *> *found = [NSMutableDictionary new];
    if (root && nativeIDs.count > 0) {
        ANNCollectViews(root, nativeIDs, found);
    }
    return found;
}

@end

@implementation ANNTransitionViews {
    NSSet<NSString *> *_leavingIDs;
    NSSet<NSString *> *_targetIDs;
    NSSet<NSString *> *_elementIDs;
    NSDictionary<NSString *, UIView *> *_leavingViews;
    NSDictionary<NSString *, UIView *> *_arrivingViews;
}

- (instancetype)initWithPairs:(NSArray<ANNSharedElementTransitionOptions *> *)pairs
                     elements:(NSArray<ANNElementTransitionOptions *> *)elements
                     fromNode:(UIViewController *)fromNode
                       toNode:(UIViewController *)toNode {
    if (self = [super init]) {
        _leavingRoot = [ANNElementFinder searchRootOf:fromNode];
        _arrivingRoot = [ANNElementFinder searchRootOf:toNode];
        NSMutableSet<NSString *> *sources = [NSMutableSet new];
        NSMutableSet<NSString *> *targets = [NSMutableSet new];
        NSMutableSet<NSString *> *identifiers = [NSMutableSet new];
        for (ANNSharedElementTransitionOptions *pair in pairs) {
            NSString *fromId = ANNNativeIDOf(pair.fromId);
            NSString *toId = ANNNativeIDOf(pair.toId);
            if (fromId) {
                [sources addObject:fromId];
            }
            if (toId) {
                [targets addObject:toId];
            }
        }
        for (ANNElementTransitionOptions *element in elements) {
            NSString *identifier = ANNNativeIDOf(element.identifier);
            if (identifier) {
                [identifiers addObject:identifier];
            }
        }
        [sources unionSet:identifiers];
        _leavingIDs = sources;
        _targetIDs = targets;
        _elementIDs = identifiers;
    }
    return self;
}

- (UIView *)leavingViewWithNativeID:(NSString *)nativeID {
    if (nativeID.length == 0) {
        return nil;
    }
    if (!_leavingViews) {
        _leavingViews = [ANNElementFinder viewsWithNativeIDs:_leavingIDs inView:_leavingRoot];
    }
    return _leavingViews[nativeID];
}

- (UIView *)arrivingViewWithNativeID:(NSString *)nativeID {
    if (nativeID.length == 0) {
        return nil;
    }
    if (!_arrivingViews) {
        // An element is looked for in the arriving screen only when the leaving one doesn't have it: the walk stops
        // sooner.
        NSMutableSet<NSString *> *ids = [_targetIDs mutableCopy];
        for (NSString *identifier in _elementIDs) {
            if (![self leavingViewWithNativeID:identifier]) {
                [ids addObject:identifier];
            }
        }
        _arrivingViews = [ANNElementFinder viewsWithNativeIDs:ids inView:_arrivingRoot];
    }
    return _arrivingViews[nativeID];
}

@end

@implementation ANNIsolatedElement {
    id<ANNFrameClient> _client;
    NSString *_name;
    BOOL _failed;
}

- (instancetype)initWithClient:(id<ANNFrameClient>)client name:(NSString *)name {
    if (self = [super init]) {
        _client = client;
        _name = [name copy];
    }
    return self;
}

#pragma mark - ANNFrameClient

- (void)applyAtTime:(NSTimeInterval)time {
    if (_failed) {
        return;
    }
    id<ANNFrameClient> client = _client;
    if (!ANNElementStep(_name, ^{
          [client applyAtTime:time];
        })) {
        _failed = YES;
        [self restore];
    }
}

- (NSTimeInterval)endTime {
    return _client.endTime;
}

- (void)restore {
    id<ANNFrameClient> client = _client;
    ANNElementStep(_name, ^{
      [client restore];
    });
}

@end
