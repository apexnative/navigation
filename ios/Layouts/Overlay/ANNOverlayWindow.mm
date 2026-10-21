#import "ANNOverlayWindow.h"
#import "ANNReactView.h"
#import "UIViewController+LayoutProtocol.h"

@implementation ANNOverlayWindow

// A field of the overlay gets the keyboard as one on a screen does: the window becomes key as the user touches it,
// before the field takes the touch (a field of a window that is not key does not become first responder).
- (void)sendEvent:(UIEvent *)event {
    if (!self.isKeyWindow && event.type == UIEventTypeTouches) {
        for (UITouch *touch in event.allTouches) {
            if (touch.phase == UITouchPhaseBegan && ANNIsTextInput(touch.view)) {
                _takesKeyboard = YES;
                [self makeKeyWindow];
                break;
            }
        }
    }
    [super sendEvent:event];
}

static BOOL ANNIsTextInput(UIView *view) {
    for (UIView *current = view; current; current = current.superview) {
        if ([current conformsToProtocol:@protocol(UITextInput)]) {
            return YES;
        }
    }
    return NO;
}

// A touch that no view of the overlay takes (a wrapper with `pointerEvents="box-none"` takes
// none) reaches the windows under it, unless the overlay has `interceptTouchOutside`.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    UIView *hit = [super hitTest:point withEvent:event];
    if (!hit || ![self isEmptyArea:hit]) {
        return hit;
    }
    return self.interceptsTouchOutside ? hit : nil;
}

// The window, the containers UIKit puts the overlay in, the view of a screen, and from a screen's content view down to
// the root view of its React surface, which carries the surface's root tag (in one tree, the slot's content view under
// the slot screen view). Decided by the hit alone, without React Native's view classes.
- (BOOL)isEmptyArea:(UIView *)hit {
    if ([self.rootViewController.view isDescendantOfView:hit] ||
        [hit.nextResponder isKindOfClass:UIViewController.class]) {
        return YES;
    }
    // The screen's content view the hit is in; a hit outside every content (a bar's button) is the overlay's.
    UIView *content = hit;
    while (content && ![content conformsToProtocol:@protocol(ANNComponentProtocol)]) {
        content = content.superview;
    }
    if (!content) {
        return NO;
    }
    RCTFabricSurface *surface = ((id<ANNComponentProtocol>)content).surface;
    if (!surface) {
        return hit == content || hit.superview == content;
    }
    // A hit under the surface's root view is React content: the root view is between it and the content view.
    NSInteger rootTag = surface.rootTag;
    for (UIView *view = hit.superview; view && view != content; view = view.superview) {
        if (view.tag == rootTag) {
            return NO;
        }
    }
    return YES;
}

@end
