#import "ANNSplitViewCommands.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNCommandRunner.h"
#import "ANNComponentViewController.h"
#import "ANNErrorTexts.h"
#import "ANNLayoutFactory.h"
#import "ANNLayoutProtocol.h"
#import "ANNLayoutTreeOptions.h"
#import "ANNLayoutValidator.h"
#import "ANNModalCoordinator.h"
#import "ANNNodeRegistry.h"
#import "ANNParking.h"
#import "ANNPendingLayers.h"
#import "ANNRenderWait.h"
#import "ANNShownScreens.h"
#import "ANNStackCommands.h"
#import "ANNStackController.h"
#import "ANNStackQueue.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>

// The limits of the bar components of a push: title and background 1 s, buttons 0.5 s. With `waitForRender` unset they
// count from the wait's start, as a stack's push; with `true`, from the content's end.
static const ANNBarWaitLimits ANNColumnBarLimits = {1, 0.5};

// How long the push's wait waits for the screen's first content when `waitForRender` is not set, as a stack's push.
static const NSTimeInterval ANNColumnRenderLimit = 0.5;

// The split view of `node`: the node itself or the nearest of its layout parents that has columns.
static UIViewController *ANNSplitViewOf(UIViewController *node) {
    for (UIViewController *parent = node; parent; parent = parent.layoutParent) {
        if ([parent respondsToSelector:@selector(viewControllerOfColumn:)]) {
            return parent;
        }
    }
    return nil;
}

static BOOL ANNIsCollapsedIntoCompact(UIViewController *splitView) {
    return [splitView respondsToSelector:@selector(isCollapsedIntoCompact)] &&
           [(id<ANNLayoutProtocol>)splitView isCollapsedIntoCompact];
}

// The node is seen: in a window, and not inside a layer that waits to show.
static BOOL ANNIsOnScreen(UIViewController *node) {
    return node.viewIfLoaded.window != nil && ![ANNPendingLayers isWaitingToShow:node];
}

@class ANNColumnRequest;

// A request the newest of its column superseded: its answer and the id it resolves with, its own screen's (each
// `showInColumn` answers its own screen, as on Android).
@interface ANNSupersededRequest : NSObject
@property(nonatomic, strong) ANNCommandAnswer *answer;
@property(nonatomic, copy) NSString *value;
@end

@implementation ANNSupersededRequest
@end

// "The last request of a column" (`showInColumn` step 2): one per split view and column. The newest request decides
// when the requests it superseded answer; each of them resolves with its own screen's id whatever the newest one's
// outcome, as on Android.
@interface ANNColumnRecord : NSObject
@property(nonatomic, weak, nullable) UIViewController *splitView;
@property(nonatomic, copy) NSString *column;
// Weakly: what runs the newest request holds it (the waiting requests, the split view's call, the push).
@property(nonatomic, weak, nullable) ANNColumnRequest *newest;
@property(nonatomic, strong) NSMutableArray<ANNSupersededRequest *> *superseded;
@property(nonatomic) BOOL ended;
@end

@implementation ANNColumnRecord
@end

// One `showInColumn` from its start to its answer. It holds its screen: the registry and the pending layers hold
// nodes weakly.
@interface ANNColumnRequest : NSObject
@property(nonatomic, strong) ANNCommandAnswer *answer;
@property(nonatomic, strong) UIViewController *screen;
@property(nonatomic, strong) ANNLayoutNode *layout;
@property(nonatomic, copy) NSString *column;
@property(nonatomic, copy) NSString *componentId;
@property(nonatomic, copy) NSString *what;
@property(nonatomic, weak, nullable) UIViewController *source;
@property(nonatomic, strong, nullable) ANNColumnRecord *record;
@property(nonatomic, strong, nullable) ANNRenderWait *wait;
@property(nonatomic, strong, nullable) ANNParking *parking;
@property(nonatomic) BOOL pending;
@property(nonatomic) BOOL driven;
// The new screen began to appear in the split view's show: its end closes that appearance either way.
@property(nonatomic) BOOL began;
@property(nonatomic) BOOL done;
// The made node rendered (once: a pair's `render` starts its count again).
@property(nonatomic) BOOL rendered;
// Inside the wait's `start`: a wait that ends there leaves the decision to the next turn.
@property(nonatomic) BOOL starting;
@end

@implementation ANNColumnRequest
@end

// The requests whose screen waits to be ready, in the order they began. Main queue.
static NSMutableArray<ANNColumnRequest *> *ANNWaitingRequests(void) {
    static NSMutableArray<ANNColumnRequest *> *requests;
    if (!requests) {
        requests = [NSMutableArray new];
    }
    return requests;
}

// The records of one split view, by column.
typedef NSMutableDictionary<NSString *, ANNColumnRecord *> ANNColumnRecordsOfSplitView;

// The records by split view (weakly) and column. Main queue.
static NSMapTable<UIViewController *, ANNColumnRecordsOfSplitView *> *ANNColumnRecords(void) {
    static NSMapTable<UIViewController *, ANNColumnRecordsOfSplitView *> *records;
    if (!records) {
        records = [NSMapTable weakToStrongObjectsMapTable];
    }
    return records;
}

@implementation ANNSplitViewCommands

#pragma mark - showInColumn

+ (void)showInColumn:(NSString *)column
              layout:(ANNLayoutNode *)layout
         componentId:(NSString *)componentId
                what:(NSString *)what
              answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *source = [ANNNodeRegistry nodeForComponentId:componentId];
    if (!source) {
        [answer reject:[ANNErrorTexts screenNotShown:componentId]];
        return;
    }
    // Step 2: the push's checks of the layout; what a stack takes is checked by the push, when it comes to one.
    NSString *shownId = [ANNLayoutValidator firstShownIdIn:layout allowed:nil];
    if (shownId) {
        [answer reject:[ANNErrorTexts alreadyShown:shownId]];
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
        [answer reject:[ANNErrorTexts notDoneYet:@"showInColumn"]];
        return;
    }
    // A native screen's creator runs here: its failure rejects before anything waits.
    UIViewController *screen = [ANNLayoutFactory nodeFromLayout:layout options:optionsById];
    ANNColumnRequest *request = [ANNColumnRequest new];
    request.answer = answer;
    request.screen = screen;
    request.layout = layout;
    request.column = column;
    request.componentId = componentId;
    request.what = what;
    request.source = source;
    // Step 3: the newest request of the column from now on.
    UIViewController *splitView = ANNSplitViewOf(source);
    if (splitView) {
        ANNColumnRecord *record = [self recordOf:splitView column:column];
        record.newest = request;
        request.record = record;
    }
    [ANNWaitingRequests() addObject:request];
    [self protect:request
             step:^{
               [self startWaitOf:request];
             }];
}

+ (ANNColumnRecord *)recordOf:(UIViewController *)splitView column:(NSString *)column {
    NSMapTable<UIViewController *, ANNColumnRecordsOfSplitView *> *table = ANNColumnRecords();
    ANNColumnRecordsOfSplitView *records = [table objectForKey:splitView];
    if (!records) {
        records = [NSMutableDictionary new];
        [table setObject:records forKey:splitView];
    }
    ANNColumnRecord *record = records[column];
    if (!record) {
        record = [ANNColumnRecord new];
        record.splitView = splitView;
        record.column = column;
        record.superseded = [NSMutableArray new];
        records[column] = record;
    }
    return record;
}

// Step 1, the push's wait, as a stack's push: `waitForRender` unset waits for the content at most 0.5 s, `true` as long
// as it takes (with the rule for content nobody knows of), then the bar components; `false` for nothing; nothing either
// while the source's place isn't seen. The content's end waits for the calls JavaScript made by then, so that the
// options of the screen's first commit are applied before it shows. Read from the screen's own options over the
// defaults: it has no place yet. The screen waits unseen in the source's window, as a layer that waits to show.
+ (void)startWaitOf:(ANNColumnRequest *)request {
    UIViewController *screen = request.screen;
    Bool *waitForRender = [screen resolveOptionsWithDefault].animations.push.waitForRender;
    UIViewController *source = request.source;
    BOOL onScreen = source && ANNIsOnScreen(source);
    if (!onScreen || (waitForRender.hasValue && !waitForRender.get)) {
        [self render:request];
        [self readyOnNextTurnOf:request];
        return;
    }
    BOOL bounded = !waitForRender.hasValue;
    __weak UIViewController *weakScreen = screen;
    NSArray<UIViewController *> * (^screens)(void) = ^NSArray<UIViewController *> * {
      UIViewController *waited = weakScreen;
      return waited ? waited.waitedComponentViewControllers : @[];
    };
    ANNRenderWait *wait =
        [[ANNRenderWait alloc] initWithContent:ANNRenderWaitContentNode
                                          node:screen
                                       screens:screens
                                         limit:bounded ? ANNColumnRenderLimit : 0
                                           bar:ANNRenderWaitBarAfterContent
                                     barLimits:ANNColumnBarLimits];
    wait.unknownContentRule = !bounded;
    wait.settlesThroughJavaScript = YES;
    wait.barLimitsFromStart = bounded;
    __weak ANNColumnRequest *weakRequest = request;
    wait.canEnd = ^BOOL {
      ANNColumnRequest *waiting = weakRequest;
      return waiting && !waiting.done && waiting.answer.isCurrent;
    };
    wait.protect = ^(dispatch_block_t step) {
      [ANNSplitViewCommands protect:weakRequest step:step];
    };
    wait.ended = ^{
      ANNColumnRequest *ready = weakRequest;
      if (!ready) {
          return;
      }
      // A screen that didn't render while parked makes its content right before it shows (as a push does).
      [ANNSplitViewCommands render:ready];
      if (ready.starting) {
          [ANNSplitViewCommands readyOnNextTurnOf:ready];
      } else {
          [ANNSplitViewCommands readyOf:ready];
      }
    };
    wait.setUp = ^{
      ANNColumnRequest *waiting = weakRequest;
      if (waiting) {
          [ANNSplitViewCommands park:waiting];
      }
    };
    request.wait = wait;
    request.starting = YES;
    @try {
        [wait start];
    } @finally {
        request.starting = NO;
    }
}

+ (void)render:(ANNColumnRequest *)request {
    if (!request.rendered) {
        request.rendered = YES;
        [request.screen render];
    }
}

// A screen ready at once decides on the next turn of the main queue (`showInColumn` "Крайние случаи"): a request to the
// same column made in the same turn of JavaScript is the newer one then and supersedes it. One turn, no other wait. A
// JavaScript that reloads meanwhile leaves the request waiting: the reload takes it without an answer.
+ (void)readyOnNextTurnOf:(ANNColumnRequest *)request {
    dispatch_async(dispatch_get_main_queue(), ^{
      if (!request.answer.isCurrent) {
          return;
      }
      [ANNSplitViewCommands protect:request
                               step:^{
                                 [ANNSplitViewCommands readyOf:request];
                               }];
    });
}

// The screen's view waits unseen in the window of the source (else the main window): its safe area view lays out only
// in a window. A stack command inside it tells the wait of its new top.
+ (void)park:(ANNColumnRequest *)request {
    UIViewController *screen = request.screen;
    UIWindow *window = request.source.viewIfLoaded.window ?: ApexNavigation.mainWindow;
    if (![screen isKindOfClass:ANNComponentViewController.class]) {
        [screen setWaitForRender:YES];
    }
    if (window) {
        __weak ANNColumnRequest *placed = request;
        ANNScreenPlace * (^place)(void) = ^ANNScreenPlace * {
          return [ANNSplitViewCommands placeOf:placed];
        };
        for (ANNComponentViewController *each in ANNStackScreensOfEntry(screen)) {
            each.placeWhileParked = place;
        }
        request.parking = [ANNParking new];
        [request.parking park:screen inView:window];
    }
    __weak ANNColumnRequest *weakRequest = request;
    [ANNPendingLayers add:screen
        onShownScreensChanged:^{
          ANNColumnRequest *waiting = weakRequest;
          if (!waiting || waiting.done || !waiting.wait.isWaiting) {
              return;
          }
          BOOL take = !waiting.screen.nodeHelper.destroyed;
          [ANNSplitViewCommands protect:waiting
                                   step:^{
                                     [waiting.wait retakeScreens:take];
                                   }];
        }];
    request.pending = YES;
    [self render:request];
    for (ANNComponentViewController *each in ANNStackScreensOfEntry(screen)) {
        [each updateRootLayout];
    }
}

// Where the parked screen will show, as far as it is known now: the column's stack (whose screens it replaces), the
// navigation controller UIKit keeps a column that is a screen alone in, or the source's stack while the split view
// shows as one stack. Nil without one: the screen lays out with its view's own safe area.
+ (nullable ANNScreenPlace *)placeOf:(ANNColumnRequest *)request {
    UIViewController *screen = request.screen;
    UIViewController *source = request.source;
    if (!screen || !source) {
        return nil;
    }
    UIViewController *splitView = ANNSplitViewOf(source);
    UINavigationController *stack = nil;
    if (splitView && !ANNIsCollapsedIntoCompact(splitView)) {
        UIViewController *columnNode =
            [(id<ANNLayoutProtocol>)splitView viewControllerOfColumn:request.column];
        stack = [columnNode isKindOfClass:UINavigationController.class]
                    ? (UINavigationController *)columnNode
                    : columnNode.navigationController;
    } else {
        stack = [ANNStackCommands stackOf:source].shownNavigationController;
    }
    if (!stack) {
        return nil;
    }
    return [ANNScreenPlace placeOfEntry:screen
                                inStack:stack
                                options:[ANNStackOptionsScreen(screen) resolveOptionsWithDefault]];
}

// The screen leaves its place and the waiting requests; UIKit ends its departure from the window here (it shows in
// this turn, or goes).
+ (void)leaveWaitingPlace:(ANNColumnRequest *)request {
    [request.wait cancel];
    request.wait = nil;
    if (request.pending) {
        request.pending = NO;
        [ANNPendingLayers remove:request.screen];
    }
    [request.parking unpark:request.screen endingDeparture:YES];
    request.parking = nil;
    [ANNWaitingRequests() removeObjectIdenticalTo:request];
}

// Step 3: the screen is ready; the state now decides. A source that went (destroyed, no longer the node of its id,
// or of a modal that goes with the old root) has neither a split view nor a stack.
+ (void)readyOf:(ANNColumnRequest *)request {
    if (!request || request.done) {
        return;
    }
    UIViewController *source = request.source;
    BOOL gone = !source || source.nodeHelper.destroyed ||
                [ANNNodeRegistry nodeForComponentId:request.componentId] != source ||
                [ANNModalCoordinator isWithOldRoot:source];
    UIViewController *splitView = gone ? nil : ANNSplitViewOf(source);
    if (splitView.nodeHelper.destroyed) {
        splitView = nil;
    }
    if (splitView && !ANNIsCollapsedIntoCompact(splitView)) {
        // (a) A later request of this column began meanwhile: it decides this one's answer.
        ANNColumnRecord *record = request.record;
        if (record && record.newest != request) {
            [self supersede:request];
            return;
        }
        [self show:request inSplitView:splitView];
        return;
    }
    [self leaveWaitingPlace:request];
    if (!gone && [ANNStackCommands stackOf:source]) {
        [self pushOf:request];
        return;
    }
    // (c)
    [request.screen destroy];
    NSString *text = [ANNErrorTexts neitherSplitViewNorStack:request.componentId];
    [self request:request endedWithValue:nil rejection:text];
}

// Superseded at its readiness: its screen goes without having shown (no events). It answers when the newest request
// does, at once when that answered, with its own screen's id: the newest one's rejection (a container for a stack
// column, a split view that went) is about that request only.
+ (void)supersede:(ANNColumnRequest *)request {
    [self leaveWaitingPlace:request];
    request.done = YES;
    NSString *value = ANNStackAnswerIdOf(request.screen, ANNStackAnswerCommandShowInColumn);
    [request.screen destroy];
    ANNColumnRecord *record = request.record;
    if (record.ended) {
        [request.answer resolve:value];
        return;
    }
    ANNSupersededRequest *superseded = [ANNSupersededRequest new];
    superseded.answer = request.answer;
    superseded.value = value;
    [record.superseded addObject:superseded];
}

// (a) The split view shows the screen in the column: a stack column's screens are replaced without an animation and
// without a wait, a screen alone takes the column; UIKit brings the column into view. The core drives the appearance of
// the new screen from before the call to the end of the change, and ends the show through the dispatcher:
// `DidDisappear` of the screens that left, `DidAppear` of the new one, the state, then the answer.
+ (void)show:(ANNColumnRequest *)request inSplitView:(UIViewController *)splitView {
    UIViewController *screen = request.screen;
    UIViewController *columnNode =
        [(id<ANNLayoutProtocol>)splitView viewControllerOfColumn:request.column];
    // A stack column takes what a stack takes, a screen or a pair; any other layout rejects `columnIsStack`, as on
    // Android. A column that is a screen alone takes any layout in its place.
    if ([columnNode isKindOfClass:UINavigationController.class] &&
        [ANNLayoutValidator checkStackChild:request.layout
                                    stackId:columnNode.layoutInfo.componentId ?: @""]) {
        [self leaveWaitingPlace:request];
        [screen destroy];
        [self request:request
            endedWithValue:nil
                 rejection:[ANNErrorTexts columnIsStack:request.componentId column:request.column]];
        return;
    }
    // Before the parking lets go of it, so that UIKit's calls stay silent until the end.
    [ANNAppearanceDispatcher driveAppearanceOf:@[ screen ]];
    request.driven = YES;
    [self leaveWaitingPlace:request];
    // A screen alone in a column has the split view as its parent by the layout (UIKit puts it into a navigation
    // controller of its own); a stack column's stack is its parent.
    if (columnNode && ![columnNode isKindOfClass:UINavigationController.class]) {
        [screen setExplicitLayoutParent:splitView];
    }
    BOOL onScreen = ANNIsOnScreen(splitView);
    NSArray<UIViewController *> *before = onScreen ? splitView.shownComponentViewControllers : @[];
    if (onScreen) {
        request.began = YES;
        [ANNAppearanceDispatcher beginTransitionShowing:@[ screen ]];
    }
    __weak UIViewController *weakSplitView = splitView;
    ANNTransitionCompletionBlock completion = ^{
      [ANNSplitViewCommands protect:request
                               step:^{
                                 [ANNSplitViewCommands shown:request
                                                 inSplitView:weakSplitView
                                                      before:before];
                               }];
    };
    ANNTransitionRejectionBlock rejection = ^(NSString *message, NSError *error) {
      [ANNSplitViewCommands protect:request
                               step:^{
                                 [ANNSplitViewCommands stopDriving:request];
                                 [request.screen destroy];
                                 [ANNSplitViewCommands request:request
                                                endedWithValue:nil
                                                     rejection:message];
                               }];
    };
    [(id<ANNLayoutProtocol>)splitView showScreen:screen
                                        inColumn:request.column
                              fromViewController:request.source ?: splitView
                                      completion:completion
                                       rejection:rejection];
}

+ (void)shown:(ANNColumnRequest *)request
    inSplitView:(UIViewController *)splitView
         before:(NSArray<UIViewController *> *)before {
    if (request.done) {
        return;
    }
    UIViewController *screen = request.screen;
    BOOL onScreen = splitView && ANNIsOnScreen(splitView);
    NSMutableArray<UIViewController *> *hidden = [NSMutableArray new];
    for (UIViewController *each in before) {
        if (each != screen && !each.viewIfLoaded.window) {
            [hidden addObject:each];
        }
    }
    BOOL inWindow = screen.viewIfLoaded.window != nil;
    NSArray<UIViewController *> *shown = onScreen && inWindow ? @[ screen ] : @[];
    // Begun and not in a window at the end (the 1 s limit before UIKit placed it, a column kept out of view): its
    // `DidDisappear` balances the `WillAppear`; UIKit reports it later, undriven.
    if (request.began && !inWindow) {
        [hidden addObject:screen];
    }
    [ANNAppearanceDispatcher endTransitionHiding:hidden
                                         showing:shown
                                          events:nil
                                          answer:nil
                                           value:nil];
    [self stopDriving:request];
    // The column shows another screen: a waiting layer, a modal's hold and the system UI follow it (a stack column's
    // replace told them already; asking again changes nothing).
    if (splitView) {
        [ANNShownScreens changedIn:splitView];
    }
    [self request:request
        endedWithValue:ANNStackAnswerIdOf(screen, ANNStackAnswerCommandShowInColumn)
             rejection:nil];
}

+ (void)stopDriving:(ANNColumnRequest *)request {
    if (request.driven) {
        request.driven = NO;
        [ANNAppearanceDispatcher stopDrivingAppearanceOf:@[ request.screen ]];
    }
}

// (b) A push over the source: the push's checks of that stack, its animation and its answer, without a second wait. Its
// answer is this request's outcome.
+ (void)pushOf:(ANNColumnRequest *)request {
    RCTPromiseResolveBlock resolve = ^(id value) {
      NSString *shownId = [value isKindOfClass:NSString.class] ? value : @"";
      [ANNSplitViewCommands request:request endedWithValue:shownId rejection:nil];
    };
    RCTPromiseRejectBlock reject = ^(NSString *code, NSString *message, NSError *error) {
      [ANNSplitViewCommands request:request endedWithValue:nil rejection:message ?: @""];
    };
    // Without a `commandId`: the request's own answer sends `ANN.CommandCompleted`.
    ANNCommandAnswer *push = [[ANNCommandAnswer alloc] initWithCommandName:@"showInColumn"
                                                                 commandId:nil
                                                                generation:request.answer.generation
                                                                   resolve:resolve
                                                                    reject:reject];
    [ANNStackCommands pushMadeNode:request.screen
                            layout:request.layout
                       componentId:request.componentId
                              what:request.what
                            answer:push];
}

#pragma mark - The answers

// The request answers once; when it is the newest of its column, the requests it superseded answer then, each with its
// own screen's id.
+ (void)request:(ANNColumnRequest *)request
    endedWithValue:(NSString *)value
         rejection:(NSString *)rejection {
    if (request.done) {
        return;
    }
    request.done = YES;
    [self answer:request.answer value:value rejection:rejection];
    ANNColumnRecord *record = request.record;
    if (!record || record.newest != request) {
        return;
    }
    record.ended = YES;
    record.newest = nil;
    NSArray<ANNSupersededRequest *> *superseded = [record.superseded copy];
    [record.superseded removeAllObjects];
    for (ANNSupersededRequest *each in superseded) {
        [each.answer resolve:each.value];
    }
    // A request that begins from now on starts a record of its own.
    UIViewController *splitView = record.splitView;
    ANNColumnRecordsOfSplitView *records =
        splitView ? [ANNColumnRecords() objectForKey:splitView] : nil;
    if (records[record.column] == record) {
        [records removeObjectForKey:record.column];
    }
}

+ (void)answer:(ANNCommandAnswer *)answer value:(NSString *)value rejection:(NSString *)rejection {
    if (rejection) {
        [answer reject:rejection];
    } else {
        [answer resolve:value];
    }
}

// The steps after the command's body run outside the runner's protection: a failure rejects the request with its error
// text and destroys its screen.
+ (void)protect:(ANNColumnRequest *)request step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        NSString *what = request.what ?: @"showInColumn";
        NSString *text = [ANNCommandRunner rejectionOf:exception what:what];
        if (!request || request.done) {
            RCTLogWarn(@"[ApexNavigation] %@", text);
            return;
        }
        [self leaveWaitingPlace:request];
        [self stopDriving:request];
        [request.screen destroy];
        [self request:request endedWithValue:nil rejection:text];
    }
}

#pragma mark - setColumnShown

+ (void)setColumnShown:(NSString *)column
                 shown:(BOOL)shown
           componentId:(NSString *)componentId
                answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = [ANNNodeRegistry nodeForComponentId:componentId];
    UIViewController *found = node ? ANNSplitViewOf(node) : nil;
    // The columns are UIKit's: the node with columns is a UIKit split view (a UIKit class, not the module's).
    if (![found isKindOfClass:UISplitViewController.class]) {
        [answer reject:[ANNErrorTexts notInSplitView:componentId]];
        return;
    }
    UISplitViewController *splitView = (UISplitViewController *)found;
    BOOL named = [column isEqualToString:@"supplementary"] || [column isEqualToString:@"inspector"];
    NSString *name = named ? column : @"primary";
    // Without that column, or the inspector before iOS 26: nothing moves.
    if (![(id<ANNLayoutProtocol>)found viewControllerOfColumn:name]) {
        [answer resolve:nil];
        return;
    }
    UISplitViewControllerColumn uiColumn = UISplitViewControllerColumnPrimary;
    if ([name isEqualToString:@"inspector"]) {
        if (@available(iOS 26.0, *)) {
            uiColumn = UISplitViewControllerColumnInspector;
        } else {
            [answer resolve:nil];
            return;
        }
    } else if ([name isEqualToString:@"supplementary"]) {
        uiColumn = UISplitViewControllerColumnSupplementary;
    }
    if (shown) {
        [splitView showColumn:uiColumn];
    } else {
        [splitView hideColumn:uiColumn];
    }
    // UIKit's coordinator is there right after the call when a transition started (FACT F1a).
    id<UIViewControllerTransitionCoordinator> coordinator = splitView.transitionCoordinator;
    void (^ended)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          [answer resolve:nil];
        };
    BOOL waits = coordinator && [coordinator animateAlongsideTransition:nil completion:ended];
    if (!waits) {
        [answer resolve:nil];
    }
}

#pragma mark - The reload

+ (NSArray<UIViewController *> *)takeAllForReload {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *screens = [NSMutableArray new];
    for (ANNColumnRequest *request in [ANNWaitingRequests() copy]) {
        request.done = YES;
        request.record = nil;
        [self leaveWaitingPlace:request];
        [screens addObject:request.screen];
    }
    [ANNWaitingRequests() removeAllObjects];
    [ANNColumnRecords() removeAllObjects];
    return screens;
}

@end
