#import "ANNScrollEdgeEffects.h"
#import "ANNBarAppearance.h"

// What one edge gets: whether it is hidden and its style; `nil` style is automatic.
typedef struct {
    BOOL hidden;
    NSString *__unsafe_unretained _Nullable style;
} ANNEdgeEffect;

static ANNEdgeEffect ANNEdgeEffectOf(ANNScrollEdgeEffectOptions *effect,
                                     ANNScrollEdgeOptions *edge) {
    ANNEdgeEffect result;
    result.hidden = edge.hidden.hasValue ? edge.hidden.get : [effect.hidden withDefault:NO];
    result.style = edge.style.hasValue ? [edge.style get] : [effect.style withDefault:nil];
    return result;
}

@implementation ANNScrollEdgeEffects {
    __weak UIView *_screenView;
    NSHashTable<UIScrollView *> *_lists;
}

- (instancetype)initWithScreenView:(UIView *)screenView {
    if (self = [super init]) {
        _screenView = screenView;
        _lists = [NSHashTable weakObjectsHashTable];
    }
    return self;
}

- (void)applyToAllWithOptions:(ANNNavigationOptions *)options {
    if (@available(iOS 26.0, *)) {
        [self reset];
        UIView *screenView = _screenView;
        if (screenView) {
            [self applyIn:screenView options:options];
        }
    }
}

- (void)applyIn:(UIView *)view options:(ANNNavigationOptions *)options API_AVAILABLE(ios(26.0)) {
    for (UIView *subview in view.subviews) {
        if ([subview isKindOfClass:UIScrollView.class]) {
            [self apply:options to:(UIScrollView *)subview];
        }
        [self applyIn:subview options:options];
    }
}

- (void)applyToList:(UIScrollView *)list options:(ANNNavigationOptions *)options {
    if (@available(iOS 26.0, *)) {
        // Lists React took out of the screen (and gave to another one) get the system values now.
        UIView *screenView = _screenView;
        for (UIScrollView *kept in _lists.allObjects) {
            if (!screenView || ![kept isDescendantOfView:screenView]) {
                [ANNScrollEdgeEffects resetList:kept];
                [_lists removeObject:kept];
            }
        }
        if (list) {
            [self apply:options to:list];
        }
    }
}

- (void)reset {
    if (@available(iOS 26.0, *)) {
        for (UIScrollView *list in _lists.allObjects) {
            [ANNScrollEdgeEffects resetList:list];
        }
    }
    [_lists removeAllObjects];
}

+ (void)resetList:(UIScrollView *)list API_AVAILABLE(ios(26.0)) {
    NSArray<UIScrollEdgeEffect *> *effects = @[
        list.topEdgeEffect, list.bottomEdgeEffect, list.leftEdgeEffect, list.rightEdgeEffect
    ];
    for (UIScrollEdgeEffect *effect in effects) {
        effect.hidden = NO;
        effect.style = UIScrollEdgeEffectStyle.automaticStyle;
    }
}

+ (UIScrollEdgeEffectStyle *)styleNamed:(NSString *)name API_AVAILABLE(ios(26.0)) {
    if ([name isEqualToString:@"soft"]) {
        return UIScrollEdgeEffectStyle.softStyle;
    }
    if ([name isEqualToString:@"hard"]) {
        return UIScrollEdgeEffectStyle.hardStyle;
    }
    return UIScrollEdgeEffectStyle.automaticStyle;
}

- (void)apply:(ANNNavigationOptions *)options to:(UIScrollView *)list API_AVAILABLE(ios(26.0)) {
    ANNScrollEdgeEffectOptions *effect = options.scrollEdgeEffect;
    ANNEdgeEffect top = ANNEdgeEffectOf(effect, effect.top);
    // Under a solid bar color or a React background the top effect, made for the glass, would look like a shadow:
    // none, unless the screen decided the top itself. A clear color is a clear bar: it keeps it.
    ANNTopBarBackgroundOptions *background = options.topBar.background;
    UIView *screenView = _screenView;
    UITraitCollection *traits = (screenView ?: list).traitCollection;
    BOOL filled = [ANNBarAppearance solidColorOf:background.color traits:traits] != nil ||
                  [background.component.name withDefault:nil].length > 0;
    BOOL ownTop = effect.top.hidden.hasValue || effect.top.style.hasValue ||
                  effect.hidden.hasValue || effect.style.hasValue;
    if (filled && !ownTop) {
        top.hidden = YES;
    }
    ANNEdgeEffect edges[] = {top, ANNEdgeEffectOf(effect, effect.bottom),
                             ANNEdgeEffectOf(effect, effect.left),
                             ANNEdgeEffectOf(effect, effect.right)};
    NSArray<UIScrollEdgeEffect *> *effects =
        @[ list.topEdgeEffect, list.bottomEdgeEffect, list.leftEdgeEffect, list.rightEdgeEffect ];
    for (NSUInteger index = 0; index < effects.count; index++) {
        effects[index].hidden = edges[index].hidden;
        effects[index].style = [ANNScrollEdgeEffects styleNamed:edges[index].style];
    }
    [_lists addObject:list];
}

@end
