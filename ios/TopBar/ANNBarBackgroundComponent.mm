#import "ANNBarBackgroundComponent.h"
#import <React/RCTLog.h>
#import <objc/runtime.h>

// The background a bar shows now.
static const void *ANNBarBackgroundShownKey = &ANNBarBackgroundShownKey;

// The view the components go into: UIKit's own background view of the bar, found by the end of its class name among
// all the bar's subviews (with a blur it isn't the first); without it the bar itself, over the system background at
// index 1. The path without UIKit's view comes later.
static UIView *ANNBackgroundHostOf(UINavigationBar *bar) {
    for (UIView *subview in bar.subviews) {
        if ([NSStringFromClass(subview.class) hasSuffix:@"BarBackground"]) {
            return subview;
        }
    }
#if DEBUG
    // The name is UIKit's private one: a system that renames the view puts the components on the bar itself. Told
    // once, in development, and only for a bar that has its views already (one not laid out yet has none).
    static BOOL told = NO;
    if (!told && bar.subviews.count > 0) {
        told = YES;
        NSMutableArray<NSString *> *names = [NSMutableArray new];
        for (UIView *subview in bar.subviews) {
            [names addObject:NSStringFromClass(subview.class)];
        }
        RCTLogInfo(@"[ApexNavigation] the bar has no view named …BarBackground, so a background "
                   @"component goes on the bar itself at index 1; its views: %@",
                   [names componentsJoinedByString:@", "]);
    }
#endif
    return bar;
}

@interface ANNBarBackgroundComponent ()
// What goes into the bar: the content in a view that fills the bar's background. A message to nil is safe on it.
@property(nonatomic, strong, readonly) UIView *container;
@end

@implementation ANNBarBackgroundComponent

- (instancetype)initWithContent:(UIView<ANNComponentProtocol> *)content {
    if (self = [super init]) {
        _content = content;
        _container = [UIView new];
        _container.backgroundColor = UIColor.clearColor;
        _container.userInteractionEnabled = NO;
        UIViewAutoresizing fills =
            UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        _container.autoresizingMask = fills;
        content.autoresizingMask = fills;
        [_container addSubview:content];
    }
    return self;
}

- (BOOL)isShownInBar:(UINavigationBar *)bar {
    return objc_getAssociatedObject(bar, ANNBarBackgroundShownKey) == self;
}

- (void)leaveBar {
    _inList = NO;
    [_container removeFromSuperview];
}

- (void)placeInList:(UIScrollView *)list frame:(CGRect)frame {
    _inList = YES;
    if (_container.superview != list) {
        [_container removeFromSuperview];
        [list insertSubview:_container atIndex:0];
    } else if (list.subviews.firstObject != _container) {
        [list sendSubviewToBack:_container];
    }
    if (!CGRectEqualToRect(_container.frame, frame)) {
        _container.frame = frame;
    }
}

- (void)returnFromListToBar:(UINavigationBar *)bar {
    if (!_inList) {
        return;
    }
    _inList = NO;
    if (bar && [self isShownInBar:bar]) {
        [self installInBar:bar];
    } else {
        [_container removeFromSuperview];
    }
}

// Fills the host, under what UIKit draws over it; the content fills the container. A component in the top screen's
// list stays there.
- (void)installInBar:(UINavigationBar *)bar {
    if (_inList) {
        return;
    }
    UIView *host = ANNBackgroundHostOf(bar);
    if (_container.superview != host) {
        [_container removeFromSuperview];
        if (host == bar) {
            [bar insertSubview:_container atIndex:MIN((NSUInteger)1, bar.subviews.count)];
        } else {
            [host addSubview:_container];
        }
    }
    _container.frame = host.bounds;
    _content.frame = _container.bounds;
}

+ (void)show:(ANNBarBackgroundComponent *)incoming
       inBar:(UINavigationBar *)bar
 coordinator:(id<UIViewControllerTransitionCoordinator>)coordinator {
    ANNBarBackgroundComponent *outgoing = objc_getAssociatedObject(bar, ANNBarBackgroundShownKey);
    if (incoming == outgoing) {
        [incoming installInBar:bar];
        incoming.container.alpha = 1;
        return;
    }
    objc_setAssociatedObject(bar, ANNBarBackgroundShownKey, incoming,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [incoming installInBar:bar];
    BOOL animated = coordinator && coordinator.isAnimated;
    if (animated) {
        // A component that hasn't drawn yet shows at full opacity as soon as it draws, without a fade.
        BOOL fadesIn = incoming.content.isReady;
        incoming.container.alpha = fadesIn ? 0 : 1;
        BOOL along = [coordinator
            animateAlongsideTransition:^(id<UIViewControllerTransitionCoordinatorContext> context) {
              if (fadesIn) {
                  incoming.container.alpha = 1;
              }
              outgoing.container.alpha = 0;
            }
            completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
              [ANNBarBackgroundComponent transitionOfBar:bar
                                                   ended:context.isCancelled
                                                incoming:incoming
                                                outgoing:outgoing];
            }];
        if (along) {
            return;
        }
    }
    // No transition, or one that takes no animations: the end state at once.
    incoming.container.alpha = 1;
    if (outgoing && ![outgoing isShownInBar:bar]) {
        [outgoing leaveBar];
    }
}

// A cancelled swipe brings the old one back as it was; otherwise the old one leaves, unless the bar shows it again.
+ (void)transitionOfBar:(UINavigationBar *)bar
                  ended:(BOOL)cancelled
               incoming:(ANNBarBackgroundComponent *)incoming
               outgoing:(ANNBarBackgroundComponent *)outgoing {
    if (cancelled && [incoming isShownInBar:bar] && outgoing) {
        objc_setAssociatedObject(bar, ANNBarBackgroundShownKey, outgoing,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        outgoing.container.alpha = 1;
        [outgoing installInBar:bar];
        [incoming leaveBar];
        return;
    }
    if (cancelled && [incoming isShownInBar:bar] && !outgoing) {
        objc_setAssociatedObject(bar, ANNBarBackgroundShownKey, nil,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        [incoming leaveBar];
        return;
    }
    if (outgoing && ![outgoing isShownInBar:bar]) {
        [outgoing leaveBar];
        outgoing.container.alpha = 1;
    }
    if ([incoming isShownInBar:bar]) {
        incoming.container.alpha = 1;
    }
}

@end
