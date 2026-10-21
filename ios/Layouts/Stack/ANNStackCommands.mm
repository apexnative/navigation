#import "ANNStackCommands.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNErrorTexts.h"
#import "ANNLayoutFactory.h"
#import "ANNLayoutTreeOptions.h"
#import "ANNLayoutValidator.h"
#import "ANNNodeRegistry.h"
#import "ANNPreviewPush.h"
#import "ANNStackController.h"

@implementation ANNStackCommands

+ (ANNStackController *)stackOf:(UIViewController *)node {
    for (UIViewController *parent = node; parent; parent = parent.layoutParent) {
        if ([parent isKindOfClass:ANNStackController.class]) {
            return (ANNStackController *)parent;
        }
    }
    return nil;
}

// The node of `componentId`, its stack and its entry there. A node that is no longer an entry of its stack's `entries`
// (an accepted cut or replace took it, its destroy still to come) answers `missing` (two pops of one screen, or a push
// from a screen that leaves, never act twice). The entry is nil for the stack's own id.
+ (ANNStackController *)stackOf:(NSString *)componentId
                           node:(UIViewController **)nodeOut
                          entry:(UIViewController **)entryOut
                        missing:(NSString *)missing
                         answer:(ANNCommandAnswer *)answer {
    UIViewController *node = [ANNNodeRegistry nodeForComponentId:componentId];
    if (!node) {
        [answer reject:[ANNErrorTexts screenNotShown:componentId]];
        return nil;
    }
    ANNStackController *stack = [self stackOf:node];
    if (!stack) {
        [answer reject:[ANNErrorTexts notInStack:componentId]];
        return nil;
    }
    UIViewController *entry = nil;
    if (node != stack) {
        entry = [stack entryOf:node];
        if (!entry || [stack.queue.entries indexOfObjectIdenticalTo:entry] == NSNotFound) {
            [answer reject:missing];
            return nil;
        }
    }
    *nodeOut = node;
    *entryOut = entry;
    return stack;
}

#pragma mark - push

+ (void)push:(ANNLayoutNode *)layout
    componentId:(NSString *)componentId
           what:(NSString *)what
         answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = nil;
    UIViewController *entry = nil;
    ANNStackController *stack = [self stackOf:componentId
                                         node:&node
                                        entry:&entry
                                      missing:[ANNErrorTexts screenNotShown:componentId]
                                       answer:answer];
    if (!stack) {
        return;
    }
    // An id goes to one screen.
    NSString *shownId = [ANNLayoutValidator firstShownIdIn:layout allowed:nil];
    if (shownId) {
        [answer reject:[ANNErrorTexts alreadyShown:shownId]];
        return;
    }
    NSString *notTaken = [ANNLayoutValidator checkStackChild:layout
                                                     stackId:stack.layoutInfo.componentId ?: @""];
    if (notTaken) {
        [answer reject:notTaken];
        return;
    }
    NSString *rejection = nil;
    NSDictionary<NSString *, ANNNavigationOptions *> *optionsById =
        [ANNLayoutTreeOptions optionsOfTree:layout rejection:&rejection];
    if (!optionsById) {
        [answer reject:rejection];
        return;
    }
    // Every type has a maker: the guard stays for a layout of a type none was made for.
    if (![ANNLayoutFactory canMakeTrees:@[ layout ]]) {
        [answer reject:[ANNErrorTexts notDoneYet:@"push"]];
        return;
    }
    // The preview of a push (`usePreview`): its screen waits for a tap on the preview, its key kept until then.
    if (optionsById[layout.nodeId].preview.nativeID.hasValue) {
        [ANNPreviewPush push:layout
                     options:optionsById
                      source:node
                 componentId:componentId
                        what:what
                      answer:answer];
        return;
    }
    // A key of an entry of this stack: the new screen is never made.
    NSString *key = ANNStackKeyOfLayout(layout);
    BOOL wentBack = [self goBackToKey:key
                                   of:stack
                          componentId:componentId
                                 what:what
                                entry:entry
                               answer:answer];
    if (wentBack) {
        return;
    }
    // A native screen's creator runs here: its failure rejects before anything is queued.
    UIViewController *pushed = [ANNLayoutFactory nodeFromLayout:layout options:optionsById];
    [stack setKey:key ofEntry:pushed];
    [stack.queue push:pushed answer:answer what:what componentId:componentId source:entry];
}

// The entry of `stack` with `key`: on the top the command answers at once (C6); deeper the stack goes back to it as
// `popTo` does, and the command stays a push. NO when no entry has the key.
+ (BOOL)goBackToKey:(NSString *)key
                 of:(ANNStackController *)stack
        componentId:(NSString *)componentId
               what:(NSString *)what
              entry:(UIViewController *)entry
             answer:(ANNCommandAnswer *)answer {
    UIViewController *keyed = key ? [stack entryWithKey:key] : nil;
    if (!keyed) {
        return NO;
    }
    NSString *value = ANNStackAnswerIdOf(keyed, ANNStackAnswerCommandKey);
    if (keyed == stack.queue.entries.lastObject) {
        [answer resolve:value];
        return YES;
    }
    [stack.queue cutTo:keyed
                answer:answer
                 value:value
                  what:what
           componentId:componentId
                source:entry
                silent:NO];
    return YES;
}

+ (void)pushMadeNode:(UIViewController *)node
              layout:(ANNLayoutNode *)layout
         componentId:(NSString *)componentId
                what:(NSString *)what
              answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *source = nil;
    UIViewController *entry = nil;
    ANNStackController *stack = [self stackOf:componentId
                                         node:&source
                                        entry:&entry
                                      missing:[ANNErrorTexts screenNotShown:componentId]
                                       answer:answer];
    if (!stack) {
        [node destroy];
        return;
    }
    NSString *notTaken = [ANNLayoutValidator checkStackChild:layout
                                                     stackId:stack.layoutInfo.componentId ?: @""];
    if (notTaken) {
        [node destroy];
        [answer reject:notTaken];
        return;
    }
    NSString *key = ANNStackKeyOfLayout(layout);
    if (key && [stack entryWithKey:key]) {
        [node destroy];
        [self goBackToKey:key of:stack componentId:componentId what:what entry:entry answer:answer];
        return;
    }
    [stack setKey:key ofEntry:node];
    [stack.queue push:node
             answer:answer
               what:what
        componentId:componentId
             source:entry
           animated:YES
              waits:NO];
}

#pragma mark - pop

+ (void)pop:(NSString *)componentId what:(NSString *)what answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = nil;
    UIViewController *entry = nil;
    ANNStackController *stack = [self stackOf:componentId
                                         node:&node
                                        entry:&entry
                                      missing:[ANNErrorTexts screenNotShown:componentId]
                                       answer:answer];
    if (!stack) {
        return;
    }
    NSArray<UIViewController *> *entries = stack.queue.entries;
    // From the stack's id or its top: the top; from a lower screen: it and everything over it.
    UIViewController *leaving = entry ?: entries.lastObject;
    // The id of the screen the command named; from the stack's id, the id of the top that leaves.
    NSString *value =
        node == stack ? ANNStackAnswerIdOf(leaving, ANNStackAnswerCommandPop) : componentId;
    if (leaving && entries.count == 1 &&
        [self returnFromColumnOf:stack entry:leaving answer:answer value:value]) {
        return;
    }
    NSUInteger index = leaving ? [entries indexOfObjectIdenticalTo:leaving] : NSNotFound;
    if (index == NSNotFound || index == 0) {
        [answer reject:[ANNErrorTexts firstScreenOfStack:componentId]];
        return;
    }
    // Holds don't apply to commands.
    [stack.queue cutTo:entries[index - 1]
                answer:answer
                 value:value
                  what:what
           componentId:componentId
                source:entry
                silent:NO];
}

// The only entry of a stack that a split view shows as a column over the one before it goes back to that column: the
// capability `goBackFromColumnOf:completion:` up the chain gets the top entry. It moves nothing of the stack, and
// resolves once the column is back: no `ScreenPopped`.
+ (BOOL)returnFromColumnOf:(ANNStackController *)stack
                     entry:(UIViewController *)entry
                    answer:(ANNCommandAnswer *)answer
                     value:(NSString *)value {
    for (UIViewController *parent = stack.layoutParent; parent; parent = parent.layoutParent) {
        if (![parent respondsToSelector:@selector(goBackFromColumnOf:completion:)]) {
            continue;
        }
        BOOL returned = [(id<ANNLayoutProtocol>)parent goBackFromColumnOf:entry
                                                               completion:^{
                                                                 [ANNAppearanceDispatcher
                                                                     endTransitionHiding:@[]
                                                                                 showing:@[]
                                                                                  events:nil
                                                                                  answer:answer
                                                                                   value:value];
                                                               }];
        if (returned) {
            return YES;
        }
    }
    return NO;
}

#pragma mark - popTo

+ (void)popTo:(NSString *)componentId what:(NSString *)what answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = nil;
    UIViewController *target = nil;
    ANNStackController *stack = [self stackOf:componentId
                                         node:&node
                                        entry:&target
                                      missing:[ANNErrorTexts screenNotShown:componentId]
                                       answer:answer];
    if (!stack) {
        return;
    }
    if (!target) {
        [answer reject:[ANNErrorTexts notInThisStack:componentId]];
        return;
    }
    // The target on top moves nothing; it answers in its turn of the queue (C6).
    if (target == stack.queue.entries.lastObject) {
        [stack.queue noneWithValue:componentId
                            answer:answer
                       componentId:componentId
                            source:target];
        return;
    }
    [stack.queue cutTo:target
                answer:answer
                 value:componentId
                  what:what
           componentId:componentId
                source:target
                silent:NO];
}

#pragma mark - popToRoot

+ (void)popToRoot:(NSString *)componentId what:(NSString *)what answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = nil;
    UIViewController *entry = nil;
    ANNStackController *stack = [self stackOf:componentId
                                         node:&node
                                        entry:&entry
                                      missing:[ANNErrorTexts screenNotShown:componentId]
                                       answer:answer];
    if (!stack) {
        return;
    }
    NSArray<UIViewController *> *entries = stack.queue.entries;
    UIViewController *first = entries.firstObject;
    NSString *value =
        first ? ANNStackAnswerIdOf(first, ANNStackAnswerCommandPopToRoot) : componentId;
    // One entry: the command answers at once (C6).
    if (entries.count <= 1) {
        [answer resolve:value];
        return;
    }
    [stack.queue cutTo:first
                answer:answer
                 value:value
                  what:what
           componentId:componentId
                source:entry
                silent:NO];
}

#pragma mark - setStackRoot

+ (void)setStackRoot:(NSArray<ANNLayoutNode *> *)layouts
         componentId:(NSString *)componentId
                what:(NSString *)what
              answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = nil;
    UIViewController *entry = nil;
    ANNStackController *stack = [self stackOf:componentId
                                         node:&node
                                        entry:&entry
                                      missing:[ANNErrorTexts screenNotShown:componentId]
                                       answer:answer];
    if (!stack) {
        return;
    }
    // An id of this stack is allowed: its new node replaces the old one. Shown anywhere else, it rejects.
    NSArray<UIViewController *> *entries = stack.queue.entries;
    BOOL (^ofThisStack)(ANNLayoutNode *) = ^BOOL(ANNLayoutNode *shown) {
      UIViewController *holder = [ANNNodeRegistry nodeForComponentId:shown.nodeId];
      UIViewController *holderEntry = holder && holder != stack ? [stack entryOf:holder] : nil;
      return holderEntry && [entries indexOfObjectIdenticalTo:holderEntry] != NSNotFound;
    };
    for (ANNLayoutNode *layout in layouts) {
        NSString *shownId = [ANNLayoutValidator firstShownIdIn:layout allowed:ofThisStack];
        if (shownId) {
            [answer reject:[ANNErrorTexts alreadyShown:shownId]];
            return;
        }
    }
    NSString *stackId = stack.layoutInfo.componentId ?: @"";
    for (ANNLayoutNode *layout in layouts) {
        NSString *notTaken = [ANNLayoutValidator checkStackChild:layout stackId:stackId];
        if (notTaken) {
            [answer reject:notTaken];
            return;
        }
    }
    NSString *rejection = nil;
    NSDictionary<NSString *, ANNNavigationOptions *> *optionsById =
        [ANNLayoutTreeOptions optionsOfTrees:layouts rejection:&rejection];
    if (!optionsById) {
        [answer reject:rejection];
        return;
    }
    // Every type has a maker: the guard stays for a layout of a type none was made for.
    if (![ANNLayoutFactory canMakeTrees:layouts]) {
        [answer reject:[ANNErrorTexts notDoneYet:@"setStackRoot"]];
        return;
    }
    // Each tree is made on its own: a tree that fails takes what its factory made, the trees before it go here.
    NSMutableArray<UIViewController *> *nodes = [NSMutableArray new];
    @try {
        for (ANNLayoutNode *layout in layouts) {
            UIViewController *made = [ANNLayoutFactory nodeFromLayout:layout options:optionsById];
            [nodes addObject:made];
            [stack setKey:ANNStackKeyOfLayout(layout) ofEntry:made];
        }
    } @catch (NSException *exception) {
        for (UIViewController *made in nodes.reverseObjectEnumerator) {
            [made destroy];
        }
        @throw;
    }
    // The new top's options with its stack decide the animation (default on).
    UIViewController *top = nodes.lastObject;
    [top setLayoutParentFallback:stack];
    BOOL animated =
        [[top resolveOptionsWithDefault].animations.setStackRoot.enabled withDefault:YES];
    [stack.queue replaceWith:nodes
                    animated:animated
                       waits:YES
                      answer:answer
                        what:what
                 componentId:componentId
                      source:entry
                  completion:nil
                   rejection:nil];
}

@end
