#import "ANNScrollEdgeEffects.h"
#import "ANNBarAppearance.h"
#import "ANNMainList.h"

// How deep the walk looks for lists, as the main list's search.
static const NSUInteger ANNEdgeWalkMaxDepth = 16;
static const CGFloat ANNEdgeWalkTolerance = 1;

// A list that scrolls (or bounces) vertically: what is in it is its rows, which never meet a bar themselves.
static BOOL ANNScrollsVertically(UIScrollView *list) {
    return list.alwaysBounceVertical ||
           list.contentSize.height > CGRectGetHeight(list.bounds) + ANNEdgeWalkTolerance;
}

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
    // What the last walk read (nil: walk at the next call), and whether content mounted since or the walk found a
    // scroll view whose lists come and go without a mount (a pager's pages).
    NSArray *_walkInputs;
    BOOL _mountedSinceWalk;
    BOOL _walkFoundPager;
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
        UIView *screenView = _screenView;
        if (!screenView) {
            [self reset];
            return;
        }
        NSArray *inputs = [self inputsOf:options traits:screenView.traitCollection];
        if (_walkInputs && _mountsReported && !_mountedSinceWalk && !_walkFoundPager &&
            ANNOptionValuesSame(_walkInputs, inputs)) {
            return;
        }
        NSMutableArray<UIScrollView *> *found = [NSMutableArray new];
        BOOL pager = NO;
        [self collectIn:screenView depth:0 into:found pager:&pager];
        // The lists set before that the walk no longer finds get the system values; the others are set again.
        for (UIScrollView *kept in _lists.allObjects) {
            if ([found indexOfObjectIdenticalTo:kept] == NSNotFound) {
                [ANNScrollEdgeEffects resetList:kept];
                [_lists removeObject:kept];
            }
        }
        for (UIScrollView *list in found) {
            [self apply:options to:list];
        }
        _walkInputs = inputs;
        _mountedSinceWalk = NO;
        _walkFoundPager = pager;
    }
}

// What the effects of a list read: the effect's options, the bar's background (a solid one hides the top), the traits
// a clear color is told by.
- (NSArray *)inputsOf:(ANNNavigationOptions *)options traits:(UITraitCollection *)traits {
    ANNTopBarBackgroundOptions *background = options.topBar.background;
    return @[
        options.scrollEdgeEffect, background.color, background.component.name,
        @(traits.userInterfaceStyle), @(traits.accessibilityContrast)
    ];
}

// The lists of `view`, bounded as the main list's search: hidden views (`display: none`) and the core's own views are
// left out with what is in them. A view at alpha 0 is not: an animation (a fade in, a transition) changes it without a
// mount. A list that scrolls vertically and a text view are set, not looked into; a scroll view that doesn't is looked
// into for the lists of its pages, and such a walk is never skipped.
- (void)collectIn:(UIView *)view
            depth:(NSUInteger)depth
             into:(NSMutableArray<UIScrollView *> *)found
            pager:(BOOL *)pager {
    if (depth > ANNEdgeWalkMaxDepth) {
        return;
    }
    for (UIView *subview in view.subviews) {
        if (subview.hidden || [ANNMainList isExcludedView:subview]) {
            continue;
        }
        if ([subview isKindOfClass:UIScrollView.class]) {
            UIScrollView *list = (UIScrollView *)subview;
            [found addObject:list];
            if ([list isKindOfClass:UITextView.class] || ANNScrollsVertically(list)) {
                continue;
            }
            *pager = YES;
        }
        [self collectIn:subview depth:depth + 1 into:found pager:pager];
    }
}

- (void)applyToList:(UIScrollView *)list options:(ANNNavigationOptions *)options {
    _mountedSinceWalk = YES;
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
    _walkInputs = nil;
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
