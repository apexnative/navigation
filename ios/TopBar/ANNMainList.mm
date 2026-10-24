#import "ANNMainList.h"
#import <objc/runtime.h>

static const void *ANNMainListExcludedKey = &ANNMainListExcludedKey;

static const NSUInteger ANNMainListMaxDepth = 16;
static const CGFloat ANNMainListTolerance = 1;

@implementation ANNMainList {
    __weak UIView *_screenView;
    __weak UIScrollView *_list;
}

- (instancetype)initWithScreenView:(UIView *)screenView {
    if (self = [super init]) {
        _screenView = screenView;
    }
    return self;
}

+ (void)excludeView:(UIView *)view {
    objc_setAssociatedObject(view, ANNMainListExcludedKey, @YES, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

+ (BOOL)isExcludedView:(UIView *)view {
    return objc_getAssociatedObject(view, ANNMainListExcludedKey) != nil;
}

- (UIScrollView *)keptList {
    UIScrollView *list = _list;
    UIView *screenView = _screenView;
    return list && screenView && [list isDescendantOfView:screenView] ? list : nil;
}

- (UIScrollView *)list {
    UIScrollView *kept = [self keptList];
    if (kept) {
        return kept;
    }
    UIView *screenView = _screenView;
    UIScrollView *found = screenView ? [self findIn:screenView screenView:screenView depth:0] : nil;
    _list = found;
    return found;
}

- (nullable UIScrollView *)findIn:(UIView *)view
                       screenView:(UIView *)screenView
                            depth:(NSUInteger)depth {
    if (depth > ANNMainListMaxDepth) {
        return nil;
    }
    for (UIView *subview in view.subviews) {
        if (subview.hidden || subview.alpha <= 0 ||
            objc_getAssociatedObject(subview, ANNMainListExcludedKey)) {
            continue;
        }
        BOOL list = [subview isKindOfClass:UIScrollView.class] &&
                    ![subview isKindOfClass:UITextView.class];
        if (list && [self isMainPlace:(UIScrollView *)subview screenView:screenView]) {
            return (UIScrollView *)subview;
        }
        UIScrollView *found = [self findIn:subview screenView:screenView depth:depth + 1];
        if (found) {
            return found;
        }
    }
    return nil;
}

- (BOOL)isMainPlace:(UIScrollView *)list screenView:(UIView *)screenView {
    CGRect frame = [list convertRect:list.bounds toView:screenView];
    return CGRectGetMinY(frame) <= ANNMainListTolerance &&
           frame.size.width >= screenView.bounds.size.width - ANNMainListTolerance;
}

@end
