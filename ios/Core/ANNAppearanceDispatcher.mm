#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNCommandAnswer.h"
#import "ANNComponentViewController.h"
#import "ANNNavigationState.h"
#import "ANNNodeRegistry.h"
#import "ANNReactView.h"
#import "ANNScreenAppearance+Internal.h"
#import "ANNSlotContainers.h"
#import "UIViewController+LayoutProtocol.h"
#import <objc/runtime.h>

// How long the events of an appearance wait for the content to mount, from the first event held.
static const NSTimeInterval ANNAppearanceHoldLimit = 0.5;

// The key of the record's handler among the content's mount handlers.
static NSString *const ANNAppearanceMountKey = @"ANNAppearance";

static const void *ANNNativeScreenReleasedKey = &ANNNativeScreenReleasedKey;

// The nodes a command drives, each with the number of commands that drive it. Weak keys: a node that went is driven
// no more. Main queue.
static NSMapTable<UIViewController *, NSNumber *> *ANNDrivenNodes(void) {
    static NSMapTable<UIViewController *, NSNumber *> *nodes;
    if (!nodes) {
        nodes = [NSMapTable weakToStrongObjectsMapTable];
    }
    return nodes;
}

// The screens a transition began to show (`beginTransitionShowing:`) and no end reached yet. Weak: a screen that went
// is no matter. Main queue.
static NSHashTable<UIViewController *> *ANNBegunScreens(void) {
    static NSHashTable<UIViewController *> *screens;
    if (!screens) {
        screens = [NSHashTable weakObjectsHashTable];
    }
    return screens;
}

// Whether `screen` is `node` or inside it, by the layout.
static BOOL ANNIsInsideNode(UIViewController *screen, UIViewController *node) {
    for (UIViewController *parent = screen; parent; parent = parent.layoutParent) {
        if (parent == node) {
            return YES;
        }
    }
    return NO;
}

@implementation ANNAppearanceDispatcher

#pragma mark - A record

+ (void)willAppear:(ANNScreenAppearance *)appearance {
    ANNAssertMainQueue();
    if (appearance.phase != ANNAppearancePhaseHidden) {
        return;
    }
    appearance.phase = ANNAppearancePhaseAppearing;
    if (appearance.isReady) {
        [self sendWillAppear:appearance];
    } else {
        appearance.holdsWillAppear = YES;
        [self startHolding:appearance];
    }
}

+ (void)didAppear:(ANNScreenAppearance *)appearance {
    ANNAssertMainQueue();
    if (appearance.phase == ANNAppearancePhaseShown) {
        return;
    }
    if (appearance.phase == ANNAppearancePhaseHidden) {
        [self willAppear:appearance];
    }
    appearance.phase = ANNAppearancePhaseShown;
    if (!appearance.holdsWillAppear && appearance.isReady) {
        [self sendDidAppear:appearance];
    } else {
        appearance.holdsDidAppear = YES;
        [self startHolding:appearance];
    }
}

+ (void)didDisappear:(ANNScreenAppearance *)appearance {
    ANNAssertMainQueue();
    if (appearance.phase == ANNAppearancePhaseHidden) {
        return;
    }
    BOOL heard = !appearance.holdsWillAppear;
    appearance.holdsWillAppear = NO;
    appearance.holdsDidAppear = NO;
    [self stopHolding:appearance];
    appearance.phase = ANNAppearancePhaseHidden;
    // What waited for the `DidAppear` goes now: it never comes for this appearance.
    [self runAfterDidAppear:appearance];
    // JavaScript never heard this appearance start, so it hears no end either.
    if (heard) {
        [self sendDidDisappear:appearance];
    }
}

+ (void)afterDidAppearOf:(ANNScreenAppearance *)appearance run:(dispatch_block_t)block {
    ANNAssertMainQueue();
    BOOL pending = appearance && (appearance.phase == ANNAppearancePhaseAppearing ||
                                  (appearance.phase == ANNAppearancePhaseShown &&
                                   (appearance.holdsDidAppear || appearance.holdsWillAppear)));
    if (!pending) {
        block();
        return;
    }
    if (!appearance.afterDidAppear) {
        appearance.afterDidAppear = [NSMutableArray new];
    }
    [appearance.afterDidAppear addObject:[block copy]];
}

+ (void)runAfterDidAppear:(ANNScreenAppearance *)appearance {
    NSArray<dispatch_block_t> *blocks = appearance.afterDidAppear;
    if (blocks.count == 0) {
        return;
    }
    appearance.afterDidAppear = nil;
    for (dispatch_block_t block in blocks) {
        block();
    }
}

+ (void)appearanceBecameReady:(ANNScreenAppearance *)appearance {
    ANNAssertMainQueue();
    appearance.readyForGood = YES;
    [self stopHolding:appearance];
    BOOL sendsWillAppear = appearance.holdsWillAppear;
    BOOL sendsDidAppear = appearance.holdsDidAppear;
    appearance.holdsWillAppear = NO;
    appearance.holdsDidAppear = NO;
    if (sendsWillAppear) {
        [self sendWillAppear:appearance];
    }
    if (sendsDidAppear) {
        [self sendDidAppear:appearance];
    }
}

#pragma mark - The hold

// One limit per record, from the first event held; the content's mount ends the hold earlier.
+ (void)startHolding:(ANNScreenAppearance *)appearance {
    if (appearance.holding) {
        return;
    }
    appearance.holding = YES;
    NSUInteger holdNumber = ++appearance.holdNumber;
    __weak ANNScreenAppearance *weakAppearance = appearance;
    [appearance.content setContentDidMount:^{
      ANNScreenAppearance *mounted = weakAppearance;
      if (mounted.holding) {
          [ANNAppearanceDispatcher appearanceBecameReady:mounted];
      }
    }
                                    forKey:ANNAppearanceMountKey];
    int64_t delay = (int64_t)(ANNAppearanceHoldLimit * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      ANNScreenAppearance *held = weakAppearance;
      if (held.holding && held.holdNumber == holdNumber) {
          [ANNAppearanceDispatcher appearanceBecameReady:held];
      }
    });
}

+ (void)stopHolding:(ANNScreenAppearance *)appearance {
    if (!appearance.holding) {
        return;
    }
    appearance.holding = NO;
    appearance.holdNumber++;
    [appearance.content setContentDidMount:nil forKey:ANNAppearanceMountKey];
}

#pragma mark - Sending

+ (void)sendWillAppear:(ANNScreenAppearance *)appearance {
    [appearance.eventEmitter sendComponentWillAppear:appearance.componentId
                                       componentName:appearance.componentName
                                       componentType:appearance.componentType];
}

// A `DidAppear` or a `DidDisappear` that went changes what shows: the state follows in this pass of the main loop. An
// event of a JavaScript that went is dropped, and asks for no state either (a reload's old screens leaving).
+ (void)sendDidAppear:(ANNScreenAppearance *)appearance {
    [appearance.eventEmitter sendComponentDidAppear:appearance.componentId
                                      componentName:appearance.componentName
                                      componentType:appearance.componentType];
    if (appearance.eventEmitter.isCurrent) {
        [ANNNavigationState setNeedsUpdate];
    }
    [self runAfterDidAppear:appearance];
}

+ (void)sendDidDisappear:(ANNScreenAppearance *)appearance {
    [appearance.eventEmitter sendComponentDidDisappear:appearance.componentId
                                         componentName:appearance.componentName
                                         componentType:appearance.componentType];
    if (appearance.eventEmitter.isCurrent) {
        [ANNNavigationState setNeedsUpdate];
    }
}

#pragma mark - Nodes

+ (void)nodeWillAppear:(UIViewController *)node {
    ANNAssertMainQueue();
    for (UIViewController *screen in node.shownComponentViewControllers) {
        if ([screen isKindOfClass:ANNComponentViewController.class]) {
            [(ANNComponentViewController *)screen screenWillAppear];
        }
    }
}

+ (void)nodeDidAppear:(UIViewController *)node {
    ANNAssertMainQueue();
    for (UIViewController *screen in node.shownComponentViewControllers) {
        if ([screen isKindOfClass:ANNComponentViewController.class]) {
            [(ANNComponentViewController *)screen screenDidAppear];
        }
    }
}

+ (void)nodeDidDisappear:(UIViewController *)node {
    ANNAssertMainQueue();
    if ([node isKindOfClass:ANNComponentViewController.class]) {
        [(ANNComponentViewController *)node screenDidDisappear];
    }
    for (UIViewController *child in node.layoutChildren) {
        [self nodeDidDisappear:child];
    }
}

#pragma mark - Transitions

+ (void)beginTransitionShowing:(NSArray<UIViewController *> *)shown {
    ANNAssertMainQueue();
    NSHashTable<UIViewController *> *begun = ANNBegunScreens();
    for (UIViewController *node in shown) {
        for (UIViewController *screen in node.shownComponentViewControllers) {
            if ([screen isKindOfClass:ANNComponentViewController.class]) {
                [begun addObject:screen];
            }
        }
        [self nodeWillAppear:node];
    }
}

// The end of a transition closes what its start began: a screen that began to appear inside a node the end shows or
// hides, and that left the window meanwhile without being one of the node's shown screens (a compact layout UIKit
// replaced by the columns as the split view expanded), gets its `DidDisappear` instead of a `DidAppear`. While the
// transition drove it UIKit's own call was silent. The rest of the end is unchanged.
+ (void)closeBegunScreensHiding:(NSArray<UIViewController *> *)hidden
                        showing:(NSArray<UIViewController *> *)shown {
    NSHashTable<UIViewController *> *begun = ANNBegunScreens();
    if (begun.count == 0) {
        return;
    }
    NSMutableArray<UIViewController *> *gone = [NSMutableArray new];
    for (UIViewController *screen in begun.allObjects) {
        UIViewController *shownNode = nil;
        for (UIViewController *node in shown) {
            if (ANNIsInsideNode(screen, node)) {
                shownNode = node;
                break;
            }
        }
        BOOL inHidden = NO;
        for (UIViewController *node in hidden) {
            inHidden = inHidden || ANNIsInsideNode(screen, node);
        }
        if (!shownNode && !inHidden) {
            continue;
        }
        [begun removeObject:screen];
        if (!shownNode || screen.viewIfLoaded.window) {
            continue;
        }
        NSArray<UIViewController *> *shownScreens = shownNode.shownComponentViewControllers;
        if ([shownScreens indexOfObjectIdenticalTo:screen] == NSNotFound) {
            [gone addObject:screen];
        }
    }
    for (UIViewController *screen in gone) {
        [(ANNComponentViewController *)screen screenDidDisappear];
    }
}

+ (void)endTransitionHiding:(NSArray<UIViewController *> *)hidden
                    showing:(NSArray<UIViewController *> *)shown
                     events:(dispatch_block_t)events
                     answer:(ANNCommandAnswer *)answer
                      value:(id)value {
    ANNAssertMainQueue();
    [self closeBegunScreensHiding:hidden showing:shown];
    for (UIViewController *node in hidden) {
        [self nodeDidDisappear:node];
    }
    for (UIViewController *node in shown) {
        [self nodeDidAppear:node];
    }
    // The state before the transition's events, once per answer: the answer's own step before its promise finds it
    // sent. A user's transition has no answer; one whose answer went before its end (an interrupt) sends the state of
    // its end itself, as Android does.
    if (!answer) {
        [ANNNavigationState updateNow];
    } else if (answer.answered) {
        if (answer.isCurrent) {
            [ANNNavigationState updateNowFor:answer.commandName];
        }
    } else {
        [answer reportStateNow];
    }
    if (events) {
        events();
    }
    [answer resolve:value];
}

+ (void)endSwitchHiding:(NSArray<UIViewController *> *)hidden
                showing:(NSArray<UIViewController *> *)shown
                 events:(dispatch_block_t)events {
    ANNAssertMainQueue();
    [self closeBegunScreensHiding:hidden showing:shown];
    for (UIViewController *node in hidden) {
        [self nodeDidDisappear:node];
    }
    for (UIViewController *node in shown) {
        [self nodeDidAppear:node];
    }
    if (events) {
        events();
    }
}

#pragma mark - A command drives the appearance

+ (void)driveAppearanceOf:(NSArray<UIViewController *> *)nodes {
    ANNAssertMainQueue();
    NSMapTable<UIViewController *, NSNumber *> *driven = ANNDrivenNodes();
    for (UIViewController *node in nodes) {
        [driven setObject:@([driven objectForKey:node].unsignedIntegerValue + 1) forKey:node];
    }
}

+ (void)stopDrivingAppearanceOf:(NSArray<UIViewController *> *)nodes {
    ANNAssertMainQueue();
    NSMapTable<UIViewController *, NSNumber *> *driven = ANNDrivenNodes();
    for (UIViewController *node in nodes) {
        NSUInteger count = [driven objectForKey:node].unsignedIntegerValue;
        if (count > 1) {
            [driven setObject:@(count - 1) forKey:node];
        } else {
            [driven removeObjectForKey:node];
        }
    }
}

+ (BOOL)isAppearanceDriven:(UIViewController *)screen {
    ANNAssertMainQueue();
    NSMapTable<UIViewController *, NSNumber *> *driven = ANNDrivenNodes();
    if (driven.count == 0) {
        return NO;
    }
    for (UIViewController *node = screen; node; node = node.layoutParent) {
        if ([driven objectForKey:node]) {
            return YES;
        }
    }
    return NO;
}

#pragma mark - The events of a transition and of a node that goes

+ (void)screenPopped:(UIViewController *)node {
    ANNAssertMainQueue();
    NSArray<UIViewController *> *screens = @[ node ];
    if ([node respondsToSelector:@selector(screensLeavingWithNode)]) {
        screens = [(id<ANNLayoutProtocol>)node screensLeavingWithNode];
    }
    for (UIViewController *screen in screens) {
        NSString *componentId = screen.layoutInfo.componentId;
        ANNEventEmitter *eventEmitter = screen.eventEmitter ?: node.eventEmitter;
        if (componentId) {
            [eventEmitter sendScreenPopped:componentId];
        }
    }
    [node screenPopped];
}

// Down the current children while they are nodes of the layout (a view controller a screen embeds is not one, and
// asking it would make it a helper): the last screen of that chain is the visible one.
+ (UIViewController *)visibleScreenOf:(UIViewController *)node {
    ANNAssertMainQueue();
    UIViewController *screen = nil;
    for (UIViewController *child = node; child.isLayoutNode; child = [child getCurrentChild]) {
        if ([child isKindOfClass:ANNComponentViewController.class]) {
            screen = child;
        }
    }
    return screen;
}

+ (void)modalDismissed:(UIViewController *)modal count:(NSUInteger)count {
    ANNAssertMainQueue();
    if (!modal.isLayoutNode) {
        return;
    }
    UIViewController *screen = [self visibleScreenOf:modal];
    // The id and the name come from the same node; a chain with no screen gives the modal's own id and no name.
    NSString *componentId = screen ? screen.layoutInfo.componentId : modal.layoutInfo.componentId;
    if (!componentId) {
        return;
    }
    NSString *componentName =
        screen ? ANNComponentNameWithoutSlotPrefix(screen.layoutInfo.name ?: @"") : @"";
    [modal.eventEmitter sendModalDismissed:componentId
                             componentName:componentName
                           modalsDismissed:count];
}

+ (void)nativeScreenReleased:(UIViewController *)screen {
    ANNAssertMainQueue();
    if (objc_getAssociatedObject(screen, ANNNativeScreenReleasedKey)) {
        return;
    }
    objc_setAssociatedObject(screen, ANNNativeScreenReleasedKey, @YES,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    NSString *componentId = screen.layoutInfo.componentId;
    // A live node that holds the id now (a new root that reuses an explicit id) keeps JavaScript's hook and functions
    // of that id: nothing is sent, as a slot keeps a later screen's slot.
    UIViewController *holder = componentId ? [ANNNodeRegistry nodeForComponentId:componentId] : nil;
    if (componentId && (!holder || holder == screen)) {
        [screen.eventEmitter sendNativeScreenReleased:componentId];
    }
}

@end
