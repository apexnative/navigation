#import "InteractivePopGestureDelegate.h"
#import "ANNStackController.h"
#import "ANNSystemUI.h"
#import <objc/runtime.h>

// The swipes a bars' swipe recognizer was made to wait for.
static const void *ANNAwaitedSwipesKey = &ANNAwaitedSwipesKey;

@implementation InteractivePopGestureDelegate {
    __weak ANNStackController *_stack;
    BOOL _content;
    // The touch the swipe took last: where the pan started.
    __weak UITouch *_touch;
    // The recognizer this delegate is set on.
    __weak UIGestureRecognizer *_recognizer;
}

// A question of UIKit's delegate outside the public protocol.
static BOOL ANNIsOwnQuestionOfUIKit(SEL selector) {
    const char *name = sel_getName(selector);
    return name && name[0] == '_';
}

- (instancetype)init {
    return [self initWithStack:nil systemDelegate:nil content:NO];
}

- (instancetype)initWithStack:(ANNStackController *)stack
               systemDelegate:(id<UIGestureRecognizerDelegate>)systemDelegate
                      content:(BOOL)content {
    if (self = [super init]) {
        _enabled = YES;
        _stack = stack;
        _systemDelegate = systemDelegate;
        _content = content;
    }
    return self;
}

+ (InteractivePopGestureDelegate *)installOn:(UIGestureRecognizer *)recognizer
                                       stack:(ANNStackController *)stack
                                     content:(BOOL)content
                                    barSwipe:(UIGestureRecognizer *)barSwipe {
    if (!recognizer) {
        return nil;
    }
    id<UIGestureRecognizerDelegate> current = recognizer.delegate;
    InteractivePopGestureDelegate *installed = nil;
    if ([current isKindOfClass:InteractivePopGestureDelegate.class]) {
        InteractivePopGestureDelegate *ours = (InteractivePopGestureDelegate *)current;
        if (ours->_stack == stack && ours->_content == content) {
            installed = ours;
        } else {
            current = ours.systemDelegate;
        }
    }
    if (!installed) {
        installed = [[InteractivePopGestureDelegate alloc] initWithStack:stack
                                                          systemDelegate:current
                                                                 content:content];
        installed->_recognizer = recognizer;
        recognizer.delegate = installed;
    }
    if (barSwipe) {
        NSHashTable<UIGestureRecognizer *> *awaited =
            objc_getAssociatedObject(barSwipe, ANNAwaitedSwipesKey);
        if (!awaited) {
            awaited = [NSHashTable weakObjectsHashTable];
            objc_setAssociatedObject(barSwipe, ANNAwaitedSwipesKey, awaited,
                                     OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        }
        if (![awaited containsObject:recognizer]) {
            [awaited addObject:recognizer];
            [barSwipe requireGestureRecognizerToFail:recognizer];
        }
    }
    return installed;
}

+ (void)restoreSystemDelegateOf:(UIGestureRecognizer *)recognizer {
    id<UIGestureRecognizerDelegate> current = recognizer.delegate;
    if ([current isKindOfClass:InteractivePopGestureDelegate.class]) {
        recognizer.delegate = ((InteractivePopGestureDelegate *)current).systemDelegate;
    }
}

#pragma mark - UIGestureRecognizerDelegate

// The order: which of the stack's swipes plays first (`swipeBackTakes:`: the stack's own while the top's pop follows
// the finger, UIKit's otherwise), then `popGesture` (with `false` there is neither a swipe nor the hold's event), then
// one screen or a stack not at rest, then the hold (its event instead of the swipe), then the edge swipe under a hidden
// bar begins without UIKit's delegate, and otherwise UIKit's delegate decides (its direction, a vertical scroll of the
// content). A top that isn't the stack's own (a column UIKit's split view moved in) is UIKit's.
//
// Every pan in the content is offered to the content swipe, so it is first sorted: a pan that isn't a swipe back
// (vertical, the other way) is no attempt, and neither is one on a list that scrolls sideways, which takes the pan
// itself and bounces at its start, as in plain UIKit. UIKit's delegate sorts the pans while the bar shows; under a
// hidden bar it refuses them all, and the swipe sorts them itself, as the edge swipe begins there without it.
- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer {
    ANNStackController *stack = _stack;
    // One swipe plays the pop: the stack's own while the top's pop follows the finger, UIKit's otherwise.
    if (stack && ![stack swipeBackTakes:gestureRecognizer]) {
        return NO;
    }
    if (stack && ![stack swipeBackIsUIKits:gestureRecognizer]) {
        if (!_enabled || ![stack swipeBackMayBegin]) {
            return NO;
        }
        UINavigationController *navigation = stack.shownNavigationController;
        BOOL barHidden = [ANNSystemUI barHiddenOf:navigation];
        if (_content) {
            BOOL back = barHidden ? [self panGoesBack:gestureRecognizer in:navigation]
                                  : [self systemDelegateLetsBegin:gestureRecognizer];
            if (!back) {
                return NO;
            }
            if ((barHidden || stack.backIsHeld) && [self touchIsOnSidewaysList:navigation]) {
                return NO;
            }
            return ![stack holdsSwipeBack];
        }
        if ([stack holdsSwipeBack]) {
            return NO;
        }
        if (barHidden) {
            return YES;
        }
    }
    return [self systemDelegateLetsBegin:gestureRecognizer];
}

// A pan that moves more across than along, towards the edge the screens go back to (the navigation's direction).
- (BOOL)panGoesBack:(UIGestureRecognizer *)gestureRecognizer in:(UINavigationController *)navigation {
    if (![gestureRecognizer isKindOfClass:UIPanGestureRecognizer.class]) {
        return [self systemDelegateLetsBegin:gestureRecognizer];
    }
    UIView *view = navigation.view;
    CGPoint move = [(UIPanGestureRecognizer *)gestureRecognizer translationInView:view];
    if (move.x == 0 && move.y == 0) {
        move = [(UIPanGestureRecognizer *)gestureRecognizer velocityInView:view];
    }
    if (fabs(move.x) <= fabs(move.y)) {
        return NO;
    }
    BOOL rightToLeft =
        view.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    return rightToLeft ? move.x < 0 : move.x > 0;
}

// The touch lies on a scroll view that scrolls sideways (read only: the content's views are React Native's).
- (BOOL)touchIsOnSidewaysList:(UINavigationController *)navigation {
    UIView *stop = navigation.view;
    for (UIView *view = _touch.view; view && view != stop; view = view.superview) {
        if (![view isKindOfClass:UIScrollView.class]) {
            continue;
        }
        UIScrollView *list = (UIScrollView *)view;
        UIEdgeInsets inset = list.adjustedContentInset;
        CGFloat width = list.contentSize.width + inset.left + inset.right;
        if (list.scrollEnabled &&
            (list.alwaysBounceHorizontal || width > list.bounds.size.width + 0.5)) {
            return YES;
        }
    }
    return NO;
}

- (BOOL)systemDelegateLetsBegin:(UIGestureRecognizer *)gestureRecognizer {
    id<UIGestureRecognizerDelegate> systemDelegate = _systemDelegate;
    if ([systemDelegate respondsToSelector:@selector(gestureRecognizerShouldBegin:)]) {
        return [systemDelegate gestureRecognizerShouldBegin:gestureRecognizer];
    }
    return YES;
}

// Whether the swipe takes the touch (the side menu asks it for the edge it opens from): never while it is off or the
// stack has one screen. It sends nothing: the hold's event belongs to the swipe's start. Under a hidden bar UIKit's
// delegate refuses every touch of the swipes: the swipe takes them itself there, and sorts the pans as they begin.
- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
       shouldReceiveTouch:(UITouch *)touch {
    ANNStackController *stack = _stack;
    if (stack && ![stack swipeBackIsUIKits:gestureRecognizer]) {
        if (!_enabled || stack.hasOneEntry) {
            return NO;
        }
        _touch = touch;
        [stack swipeBackReceivesTouch:touch];
        if ([ANNSystemUI barHiddenOf:stack.shownNavigationController]) {
            return YES;
        }
    }
    id<UIGestureRecognizerDelegate> systemDelegate = _systemDelegate;
    if ([systemDelegate respondsToSelector:@selector(gestureRecognizer:shouldReceiveTouch:)]) {
        return [systemDelegate gestureRecognizer:gestureRecognizer shouldReceiveTouch:touch];
    }
    return YES;
}

// A list under the stack's own swipe waits for it, as for UIKit's edge swipe: a swipe from the edge goes back, also on
// a list that scrolls sideways. UIKit's swipes keep UIKit's answer.
- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
    shouldBeRequiredToFailByGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer {
    ANNStackController *stack = _stack;
    if (stack && [stack swipeBackIsStacks:gestureRecognizer]) {
        UIView *view = otherGestureRecognizer.view;
        return [view isKindOfClass:UIScrollView.class] &&
               otherGestureRecognizer == ((UIScrollView *)view).panGestureRecognizer;
    }
    id<UIGestureRecognizerDelegate> systemDelegate = _systemDelegate;
    SEL question = @selector(gestureRecognizer:shouldBeRequiredToFailByGestureRecognizer:);
    if ([systemDelegate respondsToSelector:question]) {
        return [systemDelegate gestureRecognizer:gestureRecognizer
            shouldBeRequiredToFailByGestureRecognizer:otherGestureRecognizer];
    }
    return NO;
}

#pragma mark - The rest goes to UIKit's delegate

- (void)setBarHidden:(BOOL)barHidden {
    if (_barHidden == barHidden) {
        return;
    }
    _barHidden = barHidden;
    UIGestureRecognizer *recognizer = _recognizer;
    if (recognizer.delegate == self) {
        recognizer.delegate = nil;
        recognizer.delegate = self;
    }
}

// Under a hidden bar UIKit's own questions aren't passed on: the recognizer then never asks them. A question asked
// still goes to UIKit's delegate (one the recognizer learned of before the change).
- (BOOL)respondsToSelector:(SEL)selector {
    if ([super respondsToSelector:selector]) {
        return YES;
    }
    if (_barHidden && ANNIsOwnQuestionOfUIKit(selector)) {
        return NO;
    }
    return [_systemDelegate respondsToSelector:selector];
}

- (id)forwardingTargetForSelector:(SEL)selector {
    id<UIGestureRecognizerDelegate> systemDelegate = _systemDelegate;
    if ([systemDelegate respondsToSelector:selector]) {
        return systemDelegate;
    }
    return [super forwardingTargetForSelector:selector];
}

@end
