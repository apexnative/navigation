#import "UIViewController+LayoutProtocol.h"
#import <objc/runtime.h>

static const void *ANNNodeHelperKey = &ANNNodeHelperKey;

ANNWaitedReadiness ANNWaitedReadinessOf(UIViewController *screen) {
    SEL selector = @selector(waitedReadinessOfScreen:);
    for (UIViewController *parent = screen.layoutParent; parent; parent = parent.layoutParent) {
        if ([parent respondsToSelector:selector]) {
            return [(id<ANNLayoutProtocol>)parent waitedReadinessOfScreen:screen];
        }
    }
    return ANNWaitedReadinessContent;
}

@implementation UIViewController (LayoutProtocol)

- (ANNNodeHelper *)nodeHelper {
    ANNNodeHelper *helper = objc_getAssociatedObject(self, ANNNodeHelperKey);
    if (!helper) {
        // The helper keeps its node weakly: the view controller owns it.
        helper = [[ANNNodeHelper alloc] initWithNode:self];
        objc_setAssociatedObject(self, ANNNodeHelperKey, helper, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    return helper;
}

- (BOOL)isLayoutNode {
    ANNNodeHelper *helper = objc_getAssociatedObject(self, ANNNodeHelperKey);
    return helper.layoutInfo != nil;
}

#pragma mark - The node's data

- (ANNLayoutInfo *)layoutInfo {
    return self.nodeHelper.layoutInfo;
}

- (void)setLayoutInfo:(ANNLayoutInfo *)layoutInfo {
    self.nodeHelper.layoutInfo = layoutInfo;
}

- (ANNNavigationOptions *)options {
    return self.nodeHelper.options;
}

- (void)setOptions:(ANNNavigationOptions *)options {
    self.nodeHelper.options = options;
}

- (ANNNavigationOptions *)defaultOptions {
    return self.nodeHelper.defaultOptions;
}

- (void)setDefaultOptions:(ANNNavigationOptions *)defaultOptions {
    self.nodeHelper.defaultOptions = defaultOptions;
}

- (ANNEventEmitter *)eventEmitter {
    return self.nodeHelper.eventEmitter;
}

- (void)setEventEmitter:(ANNEventEmitter *)eventEmitter {
    self.nodeHelper.eventEmitter = eventEmitter;
}

- (id<ANNComponentViewCreator>)creator {
    return self.nodeHelper.creator;
}

- (void)setCreator:(id<ANNComponentViewCreator>)creator {
    self.nodeHelper.creator = creator;
}

- (ANNNodePresenter *)presenter {
    return self.nodeHelper.presenter;
}

- (void)setPresenter:(ANNNodePresenter *)presenter {
    self.nodeHelper.presenter = presenter;
}

#pragma mark - Places

- (UIViewController *)layoutParent {
    return self.nodeHelper.layoutParent;
}

- (void)setLayoutParentFallback:(UIViewController *)layoutParent {
    self.nodeHelper.layoutParentFallback = layoutParent;
}

- (void)setExplicitLayoutParent:(UIViewController *)layoutParent {
    self.nodeHelper.explicitLayoutParent = layoutParent;
}

- (UINavigationController *)stack {
    for (UIViewController *parent = self.layoutParent; parent; parent = parent.layoutParent) {
        if ([parent isKindOfClass:UINavigationController.class]) {
            return (UINavigationController *)parent;
        }
    }
    return nil;
}

- (UIViewController *)presentedComponentViewController {
    return self.getCurrentChild.presentedComponentViewController;
}

- (UIView<ANNComponentProtocol> *)reactView {
    return nil;
}

#pragma mark - Options

- (ANNNavigationOptions *)resolveOptionsWithDefault {
    return self.nodeHelper.resolveOptionsWithDefault;
}

- (void)mergeOwnOptions:(ANNNavigationOptions *)options {
    [self.nodeHelper mergeOwnOptions:options];
}

- (void)changeOwnOptions:(void (^)(ANNNavigationOptions *options))change {
    [self.nodeHelper changeOwnOptions:change];
}

- (void)setUserChoice:(ANNUserChoice *)choice forKey:(NSString *)key {
    [self.nodeHelper setUserChoice:choice forKey:key];
}

- (ANNUserChoice *)userChoiceForKey:(NSString *)key {
    return [self.nodeHelper userChoiceForKey:key];
}

#pragma mark - Readiness

- (void)setReadyHandler:(dispatch_block_t)handler forKey:(NSString *)key {
    [self.nodeHelper setReadyHandler:handler forKey:key];
}

- (void)removeReadyHandlerForKey:(NSString *)key {
    [self.nodeHelper removeReadyHandlerForKey:key];
}

#pragma mark - ANNLayoutProtocol, the defaults

// Safe for a container too: its view controller children are its children by the layout, and it shows what its
// current child shows. A container answers itself where that is not so.
- (UIViewController *)getCurrentChild {
    return nil;
}

- (NSArray<UIViewController *> *)layoutChildren {
    return self.childViewControllers;
}

- (NSArray<UIViewController *> *)shownComponentViewControllers {
    return self.getCurrentChild.shownComponentViewControllers ?: @[];
}

// Whether `screen` is `node` or inside it, by the layout.
static BOOL ANNIsInside(UIViewController *screen, UIViewController *node) {
    for (UIViewController *parent = screen; parent; parent = parent.layoutParent) {
        if (parent == node) {
            return YES;
        }
    }
    return NO;
}

// A container that shows one child (a stack, a side menu's center) waits for what that child waits for, so that a node
// with an answer of its own (the tabs) is reached through it. A container whose shown screens are not all in
// its current child (a pair, a wide split view) waits for its shown screens. For every node without an answer of its
// own both give the same screens, in the same order: the shown screens.
- (NSArray<UIViewController *> *)waitedComponentViewControllers {
    NSArray<UIViewController *> *shown = self.shownComponentViewControllers;
    UIViewController *child = self.getCurrentChild;
    if (!child) {
        return shown;
    }
    for (UIViewController *screen in shown) {
        if (!ANNIsInside(screen, child)) {
            return shown;
        }
    }
    return child.waitedComponentViewControllers;
}

- (void)render {
    [self.nodeHelper render];
}

- (void)readyForPresentation {
    [self.nodeHelper readyForPresentation];
}

- (BOOL)waitForRender {
    return self.nodeHelper.waitForRender;
}

- (void)setWaitForRender:(BOOL)waitForRender {
    self.nodeHelper.waitForRender = waitForRender;
}

- (ANNNavigationOptions *)resolveOptions {
    return self.nodeHelper.resolveOptions;
}

// `mergeOptions` into this node (`mergeOptions`): the own options keep the merge (without its one-shot options), the
// presenter applies what it sets over the new resolved options, and the parent takes its part, without what the
// presenter keeps for the node (`optionsForParent:`). The merge still carries the one-shot options to the presenters,
// which act on them once.
- (void)mergeOptions:(ANNNavigationOptions *)options {
    [self.nodeHelper mergeOwnOptions:options];
    ANNNodePresenter *presenter = self.presenter;
    [presenter mergeOptions:options resolvedOptions:[self resolveOptionsWithDefault]];
    [self.layoutParent mergeChildOptions:presenter ? [presenter optionsForParent:options] : options
                                   child:self];
}

// A merge goes on up to the root: a node without an answer of its own (the split view) lets its presenter take its part
// (`mergeChildOptions:`) and passes the merge on without what the presenter keeps. A container that draws something for
// its children (a stack, the tabs, a pair, a side of a side menu) answers itself. A view controller that is no node
// (UIKit's own) stops it, without a helper made for it.
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child {
    if (!self.isLayoutNode) {
        return;
    }
    ANNNodePresenter *presenter = self.presenter;
    [presenter mergeChildOptions:options];
    [self.layoutParent mergeChildOptions:presenter ? [presenter optionsForParent:options] : options
                                   child:self];
}

- (void)destroyReactView {
    for (UIViewController *child in self.layoutChildren) {
        [child destroyReactView];
    }
}

// The node leaves the registry with its children: no command finds it after this. Once: a container and a command may
// both reach a node.
- (void)destroy {
    if (![self.nodeHelper markDestroyed]) {
        return;
    }
    [self.nodeHelper unregisterNode];
    for (UIViewController *child in self.layoutChildren) {
        [child destroy];
    }
}

- (void)screenPopped {
}

- (UIViewController *)systemUIChild {
    return nil;
}

@end
