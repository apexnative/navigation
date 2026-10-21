#import "ANNLayoutFactory.h"
#import "ANNArrangementState.h"
#import "ANNArrangementViewController.h"
#import "ANNAssert.h"
#import "ANNBottomTabsController.h"
#import "ANNBottomTabsPresenter.h"
#import "ANNComponentPresenter.h"
#import "ANNComponentViewController.h"
#import "ANNDefaultOptions.h"
#import "ANNErrorTexts.h"
#import "ANNExternalViewController.h"
#import "ANNLayoutInfo.h"
#import "ANNNativeScreenContext+Internal.h"
#import "ANNReactViewCreator.h"
#import "ANNSideMenuChildViewController.h"
#import "ANNSideMenuPresenter.h"
#import "ANNSideMenuViewController.h"
#import "ANNSplitViewController.h"
#import "ANNSplitViewPresenter.h"
#import "ANNStackController.h"
#import "ANNStackPresenter.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

@interface ANNLayoutFactory ()
@property(nonatomic, copy) NSDictionary<NSString *, ANNNavigationOptions *> *optionsById;
@property(nonatomic, strong) ANNEventEmitter *eventEmitter;
@property(nonatomic, strong) ANNReactViewCreator *creator;
// Every node made so far, to destroy them when a maker throws.
@property(nonatomic, strong) NSMutableArray<UIViewController *> *madeNodes;
@end

@implementation ANNLayoutFactory

// The types a maker exists for: every type of the contract. A pair only where UIKit has pairs (the reader rejects it
// elsewhere first, `pairUnsupported:`).
+ (BOOL)canMakeType:(ANNLayoutType)type {
    switch (type) {
        case ANNLayoutTypeComponent:
        case ANNLayoutTypeExternalComponent:
        case ANNLayoutTypeStack:
        case ANNLayoutTypeBottomTabs:
        // The sides are made by their side menu's maker, never alone.
        case ANNLayoutTypeSideMenuRoot:
        case ANNLayoutTypeSideMenuLeft:
        case ANNLayoutTypeSideMenuCenter:
        case ANNLayoutTypeSideMenuRight:
        case ANNLayoutTypeSplitView:
            return YES;
        case ANNLayoutTypeArrangement:
            return ANNArrangementState.canShowArrangements;
        default:
            return NO;
    }
}

+ (BOOL)canMakeTrees:(NSArray<ANNLayoutNode *> *)trees {
    for (ANNLayoutNode *tree in trees) {
        for (ANNLayoutNode *node in tree.allNodes) {
            if (![self canMakeType:node.type]) {
                return NO;
            }
        }
    }
    return YES;
}

+ (UIViewController *)nodeFromLayout:(ANNLayoutNode *)node
                             options:
                                 (NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById {
    ANNAssertMainQueue();
    ANNLayoutFactory *factory = [[self alloc] initWithOptions:optionsById];
    @try {
        return [factory makeNode:node];
    } @catch (NSException *exception) {
        // What was made of the tree goes before the command rejects: nothing half made stays known by its id.
        for (UIViewController *made in factory.madeNodes.reverseObjectEnumerator) {
            [made destroy];
        }
        @throw;
    }
}

- (instancetype)initWithOptions:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById {
    if (self = [super init]) {
        _optionsById = [optionsById copy];
        // One JavaScript for the whole tree: the nodes and their content keep the same emitter.
        _eventEmitter = ApexNavigation.eventEmitter;
        _creator = [[ANNReactViewCreator alloc] initWithEventEmitter:_eventEmitter];
        _madeNodes = [NSMutableArray new];
    }
    return self;
}

// A container's maker makes its children with this too.
- (UIViewController *)makeNode:(ANNLayoutNode *)node {
    UIViewController *made = nil;
    switch (node.type) {
        case ANNLayoutTypeComponent:
            made = [self makeComponent:node];
            break;
        case ANNLayoutTypeExternalComponent:
            made = [self makeExternalComponent:node];
            break;
        case ANNLayoutTypeStack:
            made = [self makeStack:node];
            break;
        case ANNLayoutTypeBottomTabs:
            made = [self makeBottomTabs:node];
            break;
        case ANNLayoutTypeSideMenuRoot:
            made = [self makeSideMenu:node];
            break;
        case ANNLayoutTypeSplitView:
            made = [self makeSplitView:node];
            break;
        case ANNLayoutTypeArrangement:
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
            if (@available(iOS 27.1, *)) {
                made = [self makeArrangement:node];
            }
#endif
            break;
        default:
            break;
    }
    if (!made) {
        NSString *reason = [NSString stringWithFormat:@"no maker for the type '%@'", node.typeName];
        @throw [NSException exceptionWithName:NSInternalInconsistencyException
                                       reason:reason
                                     userInfo:nil];
    }
    [_madeNodes addObject:made];
    if (!node.isSideMenuSide) {
        [made.nodeHelper registerNode];
    }
    return made;
}

- (ANNNavigationOptions *)optionsOf:(ANNLayoutNode *)node {
    return _optionsById[node.nodeId] ?: [ANNNavigationOptions new];
}

#pragma mark - Makers

// A React screen: its content (a React surface, or the slot screen view for `slot:<Name>`) comes in `render`. A
// screen's options have no one-shot field to read at creation.
- (UIViewController *)makeComponent:(ANNLayoutNode *)node {
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId
                                                                       name:node.name];
    return [[ANNComponentViewController alloc] initWithLayoutInfo:layoutInfo
                                                          creator:_creator
                                                          options:[self optionsOf:node]
                                                   defaultOptions:ANNDefaultOptions.current
                                                        presenter:[ANNComponentPresenter new]
                                                     eventEmitter:_eventEmitter
                                             childViewControllers:nil];
}

// A native screen: its context (id, `passProps` or none, `functions`, the emitter of this JavaScript) is made with the
// screen, and the app's creator is called with it now, at the layout's creation. A name nobody registered (the layout
// check rejects it first) or a creator that returns nothing rejects the command with one text; nothing of the screen is
// made then.
- (UIViewController *)makeExternalComponent:(ANNLayoutNode *)node {
    NSString *name = node.name ?: @"";
    ANNNativeScreenCreator creator = [ApexNavigation nativeScreenCreatorForName:name];
    if (!creator) {
        @throw [ANNErrorTexts rejection:[ANNErrorTexts nativeScreenNotRegistered:name]];
    }
    ANNNativeScreenContext *context =
        [[ANNNativeScreenContext alloc] initWithComponentId:node.nodeId
                                                      props:node.passProps ?: @{}
                                                  functions:node.functions
                                               eventEmitter:_eventEmitter];
    UIViewController *nativeViewController = creator(context);
    if (![nativeViewController isKindOfClass:UIViewController.class]) {
        @throw [ANNErrorTexts rejection:[ANNErrorTexts nativeScreenNotCreated:name]];
    }
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId name:name];
    ANNExternalViewController *screen =
        [[ANNExternalViewController alloc] initWithLayoutInfo:layoutInfo
                                                      creator:_creator
                                                      options:[self optionsOf:node]
                                               defaultOptions:ANNDefaultOptions.current
                                                    presenter:[ANNComponentPresenter new]
                                                 eventEmitter:_eventEmitter
                                         childViewControllers:nil];
    [screen embedNativeViewController:nativeViewController context:context];
    return screen;
}

// A stack: its screens are made here, each registered as it is made, and set as UIKit's list without an animation and
// without events (it isn't shown yet); the last one is on top. The layout check made sure it has screens and takes only
// screens and pairs. The keys of its screens come with them.
- (UIViewController *)makeStack:(ANNLayoutNode *)node {
    NSMutableArray<UIViewController *> *children = [NSMutableArray new];
    for (ANNLayoutNode *child in node.children) {
        [children addObject:[self makeNode:child]];
    }
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId
                                                                       name:node.name];
    ANNStackController *stack =
        [[ANNStackController alloc] initWithLayoutInfo:layoutInfo
                                               creator:_creator
                                               options:[self optionsOf:node]
                                        defaultOptions:ANNDefaultOptions.current
                                             presenter:[ANNStackPresenter new]
                                          eventEmitter:_eventEmitter
                                  childViewControllers:children];
    for (NSUInteger index = 0; index < children.count; index++) {
        [stack setKey:ANNStackKeyOfLayout(node.children[index]) ofEntry:children[index]];
    }
    return stack;
}

// Tabs: each child is a tab, any layout, made in `children` order; the layout check made sure there are tabs. The
// node's own options object still has the one-shot `currentTabId` / `currentTabIndex`, which choose the first tab; the
// node keeps its options without them.
- (UIViewController *)makeBottomTabs:(ANNLayoutNode *)node {
    NSMutableArray<UIViewController *> *children = [NSMutableArray new];
    for (ANNLayoutNode *child in node.children) {
        [children addObject:[self makeNode:child]];
    }
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId
                                                                       name:node.name];
    return [[ANNBottomTabsController alloc] initWithLayoutInfo:layoutInfo
                                                       creator:_creator
                                                       options:[self optionsOf:node]
                                                defaultOptions:ANNDefaultOptions.current
                                                     presenter:[ANNBottomTabsPresenter new]
                                                  eventEmitter:_eventEmitter
                                          childViewControllers:children];
}

// A side menu: each side's layout in a wrapper of its type, with the side's id from the layout (a place: never
// registered), then the side menu module. The layout check made sure of one center and at most one menu per side, each
// side with one layout. The node's own options object still has the one-shot `sideMenu.<side>.visible`, which opens a
// menu at the first show; the node keeps its options without it. The wrappers find the side menu before its view loads
// (merges, options, waits).
- (UIViewController *)makeSideMenu:(ANNLayoutNode *)node {
    NSMutableArray<ANNSideMenuChildViewController *> *parts = [NSMutableArray new];
    for (ANNLayoutNode *side in node.children) {
        ANNLayoutNode *content = side.children.firstObject;
        if (!side.isSideMenuSide || !content) {
            continue;
        }
        ANNSideMenuChildType type = ANNSideMenuChildTypeCenter;
        if (side.type == ANNLayoutTypeSideMenuLeft) {
            type = ANNSideMenuChildTypeLeft;
        } else if (side.type == ANNLayoutTypeSideMenuRight) {
            type = ANNSideMenuChildTypeRight;
        }
        UIViewController *child = [self makeNode:content];
        ANNLayoutInfo *sideInfo = [[ANNLayoutInfo alloc] initWithComponentId:side.nodeId
                                                                        name:side.name];
        ANNSideMenuChildViewController *part =
            [[ANNSideMenuChildViewController alloc] initWithLayoutInfo:sideInfo
                                                               creator:_creator
                                                               options:[self optionsOf:side]
                                                        defaultOptions:ANNDefaultOptions.current
                                                             presenter:[ANNNodePresenter new]
                                                          eventEmitter:_eventEmitter
                                                                 child:child
                                                                  type:type];
        [_madeNodes addObject:part];
        [parts addObject:part];
    }
    ANNNavigationOptions *options = [self optionsOf:node];
    ANNSideMenuPresenter *presenter = [ANNSideMenuPresenter new];
    [presenter setVisibilityAtFirstShow:options.sideMenu];
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId
                                                                       name:node.name];
    ANNSideMenuViewController *sideMenu =
        [[ANNSideMenuViewController alloc] initWithLayoutInfo:layoutInfo
                                                      creator:_creator
                                         childViewControllers:parts
                                                      options:options
                                               defaultOptions:ANNDefaultOptions.current
                                                    presenter:presenter
                                                 eventEmitter:_eventEmitter];
    // The module keeps its node's data by hand: the creator too, as every node has it.
    sideMenu.creator = _creator;
    for (ANNSideMenuChildViewController *part in parts) {
        [part setLayoutParentFallback:sideMenu];
    }
    return sideMenu;
}

// A split view: each column's layout, by the name `data.columns` gives it, then the split view module with its
// presenter; the module chooses its style and puts the columns itself. The layout check made sure of a primary and a
// secondary column and of no split view in a column; a child that is no column is not made. A column the module leaves
// out (an inspector before iPadOS 26) goes at once: it was registered. Every column has the split view as its parent by
// the layout, whatever UIKit does with it.
- (UIViewController *)makeSplitView:(ANNLayoutNode *)node {
    NSArray<NSString *> *names =
        @[ @"primary", @"supplementary", @"secondary", @"compact", @"inspector" ];
    NSDictionary<NSString *, ANNLayoutNode *> *columnNodes = node.columnChildren;
    NSMutableDictionary<NSString *, UIViewController *> *columns = [NSMutableDictionary new];
    for (NSString *name in names) {
        ANNLayoutNode *child = columnNodes[name];
        if (child) {
            columns[name] = [self makeNode:child];
        }
    }
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId
                                                                       name:node.name];
    ANNSplitViewController *splitView =
        [[ANNSplitViewController alloc] initWithLayoutInfo:layoutInfo
                                                   creator:_creator
                                                   options:[self optionsOf:node]
                                            defaultOptions:ANNDefaultOptions.current
                                                 presenter:[ANNSplitViewPresenter new]
                                              eventEmitter:_eventEmitter
                                                   columns:columns];
    for (NSString *name in names) {
        UIViewController *column = columns[name];
        if (!column) {
            continue;
        }
        if ([splitView viewControllerOfColumn:name] != column) {
            [column destroy];
            continue;
        }
        [column setExplicitLayoutParent:splitView];
    }
    return splitView;
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
// A pair: its primary and its secondary screen, in the layout's order, then the pair module with the node's data as JS
// sent it (the module applies the pair's defaults). The pair has no options of its own: its presenter applies nothing.
- (UIViewController *)makeArrangement:(ANNLayoutNode *)node API_AVAILABLE(ios(27.1)) {
    UIViewController *primary = [self makeNode:node.children[0]];
    UIViewController *secondary = [self makeNode:node.children[1]];
    ANNLayoutInfo *layoutInfo = [[ANNLayoutInfo alloc] initWithComponentId:node.nodeId
                                                                       name:node.name];
    return [[ANNArrangementViewController alloc] initWithLayoutInfo:layoutInfo
                                                            creator:_creator
                                                            options:[self optionsOf:node]
                                                     defaultOptions:ANNDefaultOptions.current
                                                          presenter:[ANNNodePresenter new]
                                                       eventEmitter:_eventEmitter
                                                            primary:primary
                                                          secondary:secondary
                                                               data:node.data];
}
#endif

@end
