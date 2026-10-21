#import "ANNZoomAnchor.h"
#import "ANNElementFinder.h"
#import "UIViewController+LayoutProtocol.h"
#import <objc/runtime.h>

static const void *ANNZoomAnchorKey = &ANNZoomAnchorKey;

// The anchor of each source screen: one per screen, the screen held weakly.
static NSMapTable<UIViewController *, UIView *> *ANNAnchorsByScreen(void) {
    static NSMapTable<UIViewController *, UIView *> *anchors;
    if (!anchors) {
        anchors = [NSMapTable weakToStrongObjectsMapTable];
    }
    return anchors;
}

@implementation ANNZoomAnchor {
    NSString *_fromId;
    ANNZoomSourceScreens _sourceScreens;
    // The screen whose anchor this zoom placed last, and that anchor.
    __weak UIViewController *_screen;
    __weak UIView *_anchor;
}

- (instancetype)initWithFromId:(NSString *)fromId
                 sourceScreens:(ANNZoomSourceScreens)sourceScreens {
    if (self = [super init]) {
        _fromId = [fromId copy];
        _sourceScreens = [sourceScreens copy];
    }
    return self;
}

// The source view on the screens now, in a window; nil without one.
- (UIView *)sourceViewOnScreen:(UIViewController *__autoreleasing *)screenOut {
    for (UIViewController *screen in _sourceScreens()) {
        UIView *view = [ANNElementFinder viewWithNativeID:_fromId inView:screen.reactView];
        if (view && view.window) {
            if (screenOut) {
                *screenOut = screen;
            }
            return view;
        }
    }
    return nil;
}

// UIKit asks for the source (at the start, at the pop or the dismissal, maybe more than once): an anchor at the
// source view's frame now.
- (UIView *)anchorView {
    UIViewController *screen = nil;
    UIView *source = [self sourceViewOnScreen:&screen];
    if (!source || !screen.isViewLoaded) {
        return nil;
    }
    UIView *root = screen.view;
    NSMapTable<UIViewController *, UIView *> *anchors = ANNAnchorsByScreen();
    UIView *anchor = [anchors objectForKey:screen];
    if (!anchor) {
        anchor = [UIView new];
        anchor.userInteractionEnabled = NO;
        anchor.backgroundColor = UIColor.clearColor;
        anchor.isAccessibilityElement = NO;
        [anchors setObject:anchor forKey:screen];
    }
    if (anchor.superview != root) {
        [root addSubview:anchor];
    }
    anchor.frame = [source convertRect:source.bounds toView:root];
    anchor.layer.cornerRadius = source.layer.cornerRadius;
    anchor.layer.cornerCurve = source.layer.cornerCurve;
    _screen = screen;
    _anchor = anchor;
    return anchor;
}

- (void)removeAnchor {
    UIViewController *screen = _screen;
    UIView *anchor = _anchor;
    if (!anchor) {
        return;
    }
    [anchor removeFromSuperview];
    NSMapTable<UIViewController *, UIView *> *anchors = ANNAnchorsByScreen();
    if (screen && [anchors objectForKey:screen] == anchor) {
        [anchors removeObjectForKey:screen];
    }
    _anchor = nil;
}

+ (BOOL)zoom:(UIViewController *)zoomed
          fromId:(NSString *)fromId
    sourceScreens:(ANNZoomSourceScreens)sourceScreens {
    if (fromId.length == 0) {
        return NO;
    }
    ANNZoomAnchor *zoom = [[ANNZoomAnchor alloc] initWithFromId:fromId sourceScreens:sourceScreens];
    if (![zoom sourceViewOnScreen:nil]) {
        return NO;
    }
    objc_setAssociatedObject(zoomed, ANNZoomAnchorKey, zoom, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    __weak ANNZoomAnchor *weakZoom = zoom;
    zoomed.preferredTransition = [UIViewControllerTransition
            zoomWithOptions:nil
         sourceViewProvider:^UIView *(UIZoomTransitionSourceViewProviderContext *context) {
           return [weakZoom anchorView];
         }];
    return YES;
}

+ (void)transitionEndedInStack:(UINavigationController *)stack {
    NSMapTable<UIViewController *, UIView *> *anchors = ANNAnchorsByScreen();
    NSMutableArray<UIViewController *> *ended = [NSMutableArray new];
    for (UIViewController *screen in anchors.keyEnumerator) {
        if (screen.stack == stack) {
            [ended addObject:screen];
        }
    }
    for (UIViewController *screen in ended) {
        [[anchors objectForKey:screen] removeFromSuperview];
        [anchors removeObjectForKey:screen];
    }
}

+ (void)transitionEndedOf:(UIViewController *)zoomed {
    if (!zoomed) {
        return;
    }
    ANNZoomAnchor *zoom = objc_getAssociatedObject(zoomed, ANNZoomAnchorKey);
    [zoom removeAnchor];
}

@end
