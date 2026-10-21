#import "ANNOverlayCoordinator.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNCommandRunner.h"
#import "ANNErrorTexts.h"
#import "ANNLayoutFactory.h"
#import "ANNLayoutTreeOptions.h"
#import "ANNLayoutValidator.h"
#import "ANNModalCoordinator.h"
#import "ANNNodeRegistry.h"
#import "ANNOverlayWindow.h"
#import "ANNParking.h"
#import "ANNPendingLayers.h"
#import "ANNReactView.h"
#import "ANNRenderWait.h"
#import "ANNSystemUI.h"
#import "ANNUtils.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>

// How long an overlay waits for its screens: the unset rule of a modal, as no option exists for overlays: their content
// at most 0.5 s, then `markReady`.
static const NSTimeInterval ANNOverlayRenderLimit = 0.5;

// The key of the overlay's handler among the content's mount handlers: whether it covers the window is checked again.
static NSString *const ANNOverlayMountKey = @"ApexNavigation.overlay";

typedef NS_ENUM(NSInteger, ANNOverlayState) {
    // Made and findable; waits for its screens with its view in the main window, unseen.
    ANNOverlayStateWaiting,
    // In its window.
    ANNOverlayStateShown,
    // Out of the list.
    ANNOverlayStateGone,
};

// One overlay.
@interface ANNOverlay : NSObject
@property(nonatomic, strong) UIViewController *root;
@property(nonatomic, copy) NSString *rootId;
@property(nonatomic) ANNOverlayState state;
@property(nonatomic, strong, nullable) ANNCommandAnswer *answer;
@property(nonatomic, copy, nullable) void (^chainStep)(BOOL shown);
@property(nonatomic, strong, nullable) ANNRenderWait *wait;
@property(nonatomic, strong) ANNParking *parking;
@property(nonatomic, strong, nullable) ANNOverlayWindow *window;
// The overlay takes the touches the app would get: `interceptTouchOutside`, or its views take the whole window (a
// dialog with a dimmed background).
@property(nonatomic) BOOL coversWindow;
@end

@implementation ANNOverlay
@end

// Main queue. Bottom to top.
static NSMutableArray<ANNOverlay *> *ANNOverlays;

@implementation ANNOverlayCoordinator

+ (void)initialize {
    if (self == ANNOverlayCoordinator.class) {
        ANNOverlays = [NSMutableArray new];
    }
}

#pragma mark - showOverlay

+ (void)showOverlay:(ANNLayoutNode *)tree answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    if (![ANNLayoutFactory canMakeTrees:@[ tree ]]) {
        [answer reject:[ANNErrorTexts notDoneYet:@"showOverlay"]];
        return;
    }
    // An id of a shown screen that is no node of the overlay this one replaces rejects.
    NSString *rootId = tree.nodeId;
    NSString *shownId = [ANNLayoutValidator
        firstShownIdIn:tree
               allowed:^BOOL(ANNLayoutNode *node) {
                 UIViewController *shown = [ANNNodeRegistry nodeForComponentId:node.nodeId];
                 return [[ANNOverlayCoordinator overlayRootIdOf:shown] isEqualToString:rootId];
               }];
    if (shownId) {
        [answer reject:[ANNErrorTexts alreadyShown:shownId]];
        return;
    }
    NSString *rejection = nil;
    NSDictionary<NSString *, ANNNavigationOptions *> *optionsById =
        [ANNLayoutTreeOptions optionsOfTree:tree rejection:&rejection];
    if (!optionsById) {
        [answer reject:rejection];
        return;
    }
    ANNOverlay *overlay = [self makeOverlay:tree options:optionsById];
    overlay.answer = answer;
    [self startWaitOf:overlay];
}

+ (ANNOverlay *)showChainOverlay:(ANNLayoutNode *)tree
                         options:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById
                           ended:(void (^)(BOOL shown))ended {
    ANNAssertMainQueue();
    ANNOverlay *overlay = nil;
    @try {
        overlay = [self makeOverlay:tree options:optionsById];
    } @catch (NSException *exception) {
        NSString *what = [NSString stringWithFormat:@"show the screen '%@'", tree.nodeId];
        [ANNCommandRunner logFailure:@"setRoot"
                                text:[ANNErrorTexts couldNot:what
                                                      reason:exception.reason ?: exception.name]];
        ended(NO);
        return nil;
    }
    overlay.chainStep = ended;
    [self startWaitOf:overlay];
    return overlay;
}

+ (void)cancelChainOverlay:(ANNOverlay *)overlay {
    ANNAssertMainQueue();
    overlay.chainStep = nil;
    if (overlay.state == ANNOverlayStateWaiting) {
        [self cancel:overlay];
    }
}

// The tree is made and renders at the window's size, as a root's; its view waits in the main window, unseen, and it is
// a layer that waits to show. A waiting overlay with the same id never shows: its show resolves with its id.
+ (ANNOverlay *)makeOverlay:(ANNLayoutNode *)tree
                    options:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById {
    UIWindow *window = ApexNavigation.mainWindow;
    UIViewController *root = [ANNLayoutFactory nodeFromLayout:tree options:optionsById];
    @try {
        [root render];
        if (window) {
            root.view.frame = window.bounds;
        }
        [root.view setNeedsLayout];
        [root.view layoutIfNeeded];
    } @catch (NSException *exception) {
        [root destroy];
        @throw;
    }
    for (ANNOverlay *waiting in ANNOverlays.copy) {
        if (waiting.state == ANNOverlayStateWaiting &&
            [waiting.rootId isEqualToString:tree.nodeId]) {
            [self cancel:waiting];
        }
    }
    ANNOverlay *overlay = [ANNOverlay new];
    overlay.root = root;
    overlay.rootId = tree.nodeId;
    overlay.state = ANNOverlayStateWaiting;
    overlay.parking = [ANNParking new];
    [ANNOverlays addObject:overlay];
    __weak ANNOverlay *weakOverlay = overlay;
    [ANNPendingLayers add:root
        onShownScreensChanged:^{
          ANNOverlay *waiting = weakOverlay;
          if (waiting.state == ANNOverlayStateWaiting && waiting.wait.isWaiting) {
              [waiting.wait retakeScreens:!waiting.root.nodeHelper.destroyed];
          }
        }];
    if (window) {
        [overlay.parking park:root inView:window];
    }
    return overlay;
}

+ (void)startWaitOf:(ANNOverlay *)overlay {
    __weak ANNOverlay *weakOverlay = overlay;
    ANNBarWaitLimits withContent = {0, 0};
    ANNRenderWait *wait = [[ANNRenderWait alloc]
        initWithContent:ANNRenderWaitContentEachScreen
                   node:nil
                screens:^NSArray<UIViewController *> * {
                  return weakOverlay.root.waitedComponentViewControllers ?: @[];
                }
                  limit:ANNOverlayRenderLimit
                    bar:ANNRenderWaitBarWithContent
              barLimits:withContent];
    wait.mountLimit = ANNOverlayRenderLimit;
    wait.protect = ^(dispatch_block_t step) {
      [ANNOverlayCoordinator protectWaitOf:weakOverlay step:step];
    };
    wait.ended = ^{
      ANNOverlay *rendered = weakOverlay;
      if (rendered.state == ANNOverlayStateWaiting) {
          [ANNOverlayCoordinator show:rendered];
      }
    };
    overlay.wait = wait;
    // The overlay is listed and parked already: a failure at the start of its wait fails it too.
    [self protectWaitOf:overlay
                   step:^{
                     [wait start];
                   }];
}

// A failure while the overlay waits rejects its show and it goes.
+ (void)protectWaitOf:(ANNOverlay *)overlay step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        if (overlay.state == ANNOverlayStateWaiting) {
            [self fail:overlay reason:exception.reason ?: exception.name];
        }
    }
}

+ (void)fail:(ANNOverlay *)overlay reason:(NSString *)reason {
    NSString *what = [NSString stringWithFormat:@"show the screen '%@'", overlay.rootId];
    NSString *text = [ANNErrorTexts couldNot:what reason:reason];
    ANNCommandAnswer *answer = overlay.answer;
    void (^chainStep)(BOOL) = overlay.chainStep;
    overlay.answer = nil;
    overlay.chainStep = nil;
    [self cancel:overlay];
    [answer reject:text];
    if (chainStep) {
        [ANNCommandRunner logFailure:@"setRoot" text:text];
        chainStep(NO);
    }
}

// The overlay's view leaves the main window for its own window, in the main window's scene (without a scene by the
// screen's bounds, given the scene when the main window gets it), of the normal level, the newest above. A shown
// overlay with its id goes first. Then its events at once, without animation, the answer; VoiceOver goes to it.
+ (void)show:(ANNOverlay *)overlay {
    UIViewController *root = overlay.root;
    // Until the show's end ran, a failure fails the overlay; after it, the overlay stays and the failure is logged.
    void (^chainStep)(BOOL) = nil;
    BOOL ended = NO;
    @try {
        for (ANNOverlay *shown in ANNOverlays.copy) {
            if (shown != overlay && shown.state == ANNOverlayStateShown &&
                [shown.rootId isEqualToString:overlay.rootId]) {
                [self dismiss:shown answer:nil];
            }
        }
        UIWindow *mainWindow = ApexNavigation.mainWindow;
        UIWindowScene *scene = mainWindow.windowScene;
        // `initWithFrame:` is deprecated from iOS 26 on and warns only with a deployment target from 26: a window
        // without a scene is made so, and joins the scene as the main window gets it.
        ANNOverlayWindow *window =
            scene ? [[ANNOverlayWindow alloc] initWithWindowScene:scene]
                  : [[ANNOverlayWindow alloc] initWithFrame:ANNUtils.mainScreen.bounds];
        window.windowLevel = UIWindowLevelNormal;
        window.backgroundColor = UIColor.clearColor;
        window.previousWindow = [self keyWindow];
        overlay.window = window;
        [self readOptionsOf:overlay];
        [ANNAppearanceDispatcher driveAppearanceOf:@[ root ]];
        @try {
            [ANNAppearanceDispatcher beginTransitionShowing:@[ root ]];
            [ANNPendingLayers remove:root];
            [overlay.parking unpark:root endingDeparture:YES];
            overlay.state = ANNOverlayStateShown;
            window.rootViewController = root;
            window.hidden = NO;
            [window layoutIfNeeded];
            // The overlay's system UI: only the groups it sets itself.
            [ANNSystemUI setNeedsUpdate];
            chainStep = overlay.chainStep;
            overlay.chainStep = nil;
            if (chainStep) {
                [ANNAppearanceDispatcher endSwitchHiding:@[] showing:@[ root ] events:nil];
                ended = YES;
                chainStep(YES);
            } else {
                [ANNAppearanceDispatcher endTransitionHiding:@[]
                                                     showing:@[ root ]
                                                      events:nil
                                                      answer:overlay.answer
                                                       value:overlay.rootId];
                ended = YES;
            }
        } @finally {
            [ANNAppearanceDispatcher stopDrivingAppearanceOf:@[ root ]];
        }
        [self watchContentOf:overlay];
        [self checkCoverOf:overlay];
        UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, root.view);
    } @catch (NSException *exception) {
        NSString *reason = exception.reason ?: exception.name;
        if (overlay.state == ANNOverlayStateWaiting) {
            [self fail:overlay reason:reason];
        } else if (overlay.state == ANNOverlayStateShown && !ended && !overlay.answer.answered) {
            [self failShown:overlay chainStep:chainStep reason:reason];
        } else {
            RCTLogWarn(@"[ApexNavigation] %@",
                       [ANNErrorTexts couldNot:@"show an overlay" reason:reason]);
        }
    }
}

// The overlay failed while it was put in its window, before its show's end (as a waiting one, `fail:`): its window
// hides and goes, its root is destroyed, its show rejects, a chain goes on.
+ (void)failShown:(ANNOverlay *)overlay
        chainStep:(void (^)(BOOL))taken
           reason:(NSString *)reason {
    NSString *what = [NSString stringWithFormat:@"show the screen '%@'", overlay.rootId];
    NSString *text = [ANNErrorTexts couldNot:what reason:reason];
    ANNCommandAnswer *answer = overlay.answer;
    void (^chainStep)(BOOL) = taken ?: overlay.chainStep;
    overlay.answer = nil;
    overlay.chainStep = nil;
    overlay.state = ANNOverlayStateGone;
    [ANNOverlays removeObjectIdenticalTo:overlay];
    ANNOverlayWindow *window = overlay.window;
    BOOL wasKey = window.isKeyWindow;
    @try {
        window.hidden = YES;
        [ANNSystemUI setNeedsUpdate];
        [overlay.root destroy];
    } @finally {
        window.rootViewController = nil;
        overlay.window = nil;
        if (wasKey) {
            [self restoreKeyWindow];
        }
        [self updateVoiceOver];
        [answer reject:text];
        if (chainStep) {
            [ANNCommandRunner logFailure:@"setRoot" text:text];
            chainStep(NO);
        }
    }
}

// The key window now: the main window's scene's, else the main window.
+ (UIWindow *)keyWindow {
    UIWindow *mainWindow = ApexNavigation.mainWindow;
    return [ANNUtils keyWindowOfScene:mainWindow.windowScene] ?: mainWindow;
}

// The touch path never resolves options; the window keeps the value.
+ (void)readOptionsOf:(ANNOverlay *)overlay {
    ANNOverlayOptions *options = [overlay.root resolveOptionsWithDefault].overlay;
    overlay.window.interceptsTouchOutside = [options.interceptTouchOutside withDefault:NO];
    // The overlay's container is its window's root view.
    if (overlay.root.isViewLoaded) {
        NSString *testID = [options.testID withDefault:nil];
        NSString *identifier = testID.length > 0 ? testID : nil;
        UIView *view = overlay.root.view;
        if (![view.accessibilityIdentifier ?: @"" isEqualToString:identifier ?: @""]) {
            view.accessibilityIdentifier = identifier;
        }
    }
}

#pragma mark - VoiceOver

// Whether the overlay's views take the whole window: its own hit test, as a touch sees it, takes the window's centre
// and its four corners. Checked at the show, at each mount of its screens' content and after merges.
+ (void)checkCoverOf:(ANNOverlay *)overlay {
    ANNOverlayWindow *window = overlay.window;
    if (!window || overlay.state != ANNOverlayStateShown) {
        return;
    }
    CGRect bounds = window.bounds;
    BOOL covers = !CGRectIsEmpty(bounds);
    if (covers) {
        CGFloat left = CGRectGetMinX(bounds) + 1;
        CGFloat right = CGRectGetMaxX(bounds) - 1;
        CGFloat top = CGRectGetMinY(bounds) + 1;
        CGFloat bottom = CGRectGetMaxY(bounds) - 1;
        CGPoint points[] = {
            CGPointMake(CGRectGetMidX(bounds), CGRectGetMidY(bounds)),
            CGPointMake(left, top),
            CGPointMake(right, top),
            CGPointMake(left, bottom),
            CGPointMake(right, bottom),
        };
        for (CGPoint point : points) {
            if (![window hitTest:point withEvent:nil]) {
                covers = NO;
                break;
            }
        }
    }
    overlay.coversWindow = covers;
    [self updateVoiceOver];
}

// The main window is hidden from VoiceOver while a shown overlay takes the touches; a toast or a mini player doesn't
// hide it.
+ (void)updateVoiceOver {
    BOOL hidden = NO;
    for (ANNOverlay *overlay in ANNOverlays) {
        if (overlay.state == ANNOverlayStateShown &&
            (overlay.window.interceptsTouchOutside || overlay.coversWindow)) {
            hidden = YES;
            break;
        }
    }
    UIWindow *mainWindow = ApexNavigation.mainWindow;
    if (mainWindow.accessibilityElementsHidden != hidden) {
        mainWindow.accessibilityElementsHidden = hidden;
    }
}

+ (void)watchContentOf:(ANNOverlay *)overlay {
    __weak ANNOverlay *weakOverlay = overlay;
    for (UIViewController *screen in overlay.root.shownComponentViewControllers) {
        [screen.reactView
            setContentDidMount:^{
              [ANNOverlayCoordinator checkCoverOf:weakOverlay];
            }
                        forKey:ANNOverlayMountKey];
    }
}

#pragma mark - dismissOverlay

+ (void)dismissOverlay:(NSString *)componentId answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = [ANNNodeRegistry nodeForComponentId:componentId];
    if (!node || [ANNModalCoordinator isWithOldRoot:node]) {
        [answer reject:[ANNErrorTexts screenNotShown:componentId]];
        return;
    }
    ANNOverlay *overlay = [self overlayOf:node];
    if (!overlay) {
        [answer reject:[ANNErrorTexts notInOverlay:componentId]];
        return;
    }
    // A waiting overlay and the shown one it would replace both go; one answer.
    NSString *rootId = overlay.rootId;
    for (ANNOverlay *same in ANNOverlays.copy) {
        if ([same.rootId isEqualToString:rootId]) {
            [self dismiss:same answer:nil];
        }
    }
    [answer resolve:rootId];
}

+ (void)dismissAllOverlays:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    NSArray<ANNOverlay *> *overlays = [ANNOverlays copy];
    for (ANNOverlay *overlay in overlays.reverseObjectEnumerator) {
        [self dismiss:overlay answer:nil];
    }
    [answer resolve:@""];
}

// A waiting overlay is cancelled: its show resolves with its id, nothing is sent. A shown one goes at once: its
// window hides, `DidDisappear` of its screens, its content stops, the answer; the key window and VoiceOver come back.
+ (void)dismiss:(ANNOverlay *)overlay answer:(ANNCommandAnswer *)answer {
    if (overlay.state == ANNOverlayStateWaiting) {
        [self cancel:overlay];
        [answer resolve:overlay.rootId];
        return;
    }
    if (overlay.state != ANNOverlayStateShown) {
        [answer resolve:overlay.rootId];
        return;
    }
    UIViewController *root = overlay.root;
    ANNOverlayWindow *window = overlay.window;
    BOOL wasKey = window.isKeyWindow;
    overlay.state = ANNOverlayStateGone;
    [ANNOverlays removeObjectIdenticalTo:overlay];
    [ANNAppearanceDispatcher driveAppearanceOf:@[ root ]];
    @try {
        window.hidden = YES;
        // The screen back on top restores what the overlay set.
        [ANNSystemUI setNeedsUpdate];
        [ANNAppearanceDispatcher endTransitionHiding:@[ root ]
                                             showing:@[]
                                              events:^{
                                                [root destroy];
                                              }
                                              answer:answer
                                               value:overlay.rootId];
    } @finally {
        [ANNAppearanceDispatcher stopDrivingAppearanceOf:@[ root ]];
        window.rootViewController = nil;
        overlay.window = nil;
        if (wasKey) {
            [self restoreKeyWindow];
        }
        [self updateVoiceOver];
        // VoiceOver reads what is on top now, not an element of the window that went.
        UIAccessibilityPostNotification(UIAccessibilityLayoutChangedNotification, nil);
    }
}

// The key window after an overlay that held it went: the topmost overlay left that took the keyboard, else the main
// window.
+ (void)restoreKeyWindow {
    for (ANNOverlay *overlay in ANNOverlays.reverseObjectEnumerator) {
        if (overlay.state == ANNOverlayStateShown && overlay.window.takesKeyboard) {
            [overlay.window makeKeyWindow];
            return;
        }
    }
    [ApexNavigation.mainWindow makeKeyWindow];
}

+ (void)cancel:(ANNOverlay *)overlay {
    [overlay.wait cancel];
    overlay.wait = nil;
    [ANNPendingLayers remove:overlay.root];
    [overlay.parking unpark:overlay.root endingDeparture:YES];
    overlay.state = ANNOverlayStateGone;
    [ANNOverlays removeObjectIdenticalTo:overlay];
    ANNCommandAnswer *answer = overlay.answer;
    void (^chainStep)(BOOL) = overlay.chainStep;
    overlay.answer = nil;
    overlay.chainStep = nil;
    [overlay.root destroy];
    [answer resolve:overlay.rootId];
    // An overlay of a `setRoot` layout that went before it showed: the chain goes on (a superseded chain cleared its
    // step first).
    if (chainStep) {
        chainStep(NO);
    }
}

#pragma mark - Questions

+ (ANNOverlay *)overlayOf:(UIViewController *)node {
    if (!node || ANNOverlays.count == 0) {
        return nil;
    }
    UIViewController *top = node;
    for (UIViewController *parent = node.layoutParent; parent; parent = parent.layoutParent) {
        top = parent;
    }
    for (ANNOverlay *overlay in ANNOverlays) {
        if (overlay.root == top && overlay.state != ANNOverlayStateGone) {
            return overlay;
        }
    }
    return nil;
}

+ (NSString *)overlayRootIdOf:(UIViewController *)node {
    ANNAssertMainQueue();
    return [self overlayOf:node].rootId;
}

+ (NSArray<UIWindow *> *)overlayWindows {
    ANNAssertMainQueue();
    NSMutableArray<UIWindow *> *windows = [NSMutableArray new];
    for (ANNOverlay *overlay in ANNOverlays) {
        if (overlay.window) {
            [windows addObject:overlay.window];
        }
    }
    return windows;
}

+ (void)mainWindowSceneDidConnect:(UIWindowScene *)windowScene {
    ANNAssertMainQueue();
    for (ANNOverlay *overlay in ANNOverlays) {
        if (overlay.window && overlay.window.windowScene != windowScene) {
            overlay.window.windowScene = windowScene;
        }
    }
}

+ (void)optionsMerged:(ANNNavigationOptions *)options into:(UIViewController *)node {
    ANNAssertMainQueue();
    ANNOverlay *overlay = [self overlayOf:node];
    if (overlay.state != ANNOverlayStateShown) {
        return;
    }
    [self readOptionsOf:overlay];
    [self checkCoverOf:overlay];
}

#pragma mark - Reload

+ (NSArray<UIViewController *> *)takeAllForReload {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *roots = [NSMutableArray new];
    BOOL keyWent = NO;
    for (ANNOverlay *overlay in ANNOverlays) {
        [overlay.wait cancel];
        overlay.wait = nil;
        [ANNPendingLayers remove:overlay.root];
        [overlay.parking unpark:overlay.root endingDeparture:YES];
        keyWent = keyWent || overlay.window.isKeyWindow;
        overlay.window.hidden = YES;
        overlay.state = ANNOverlayStateGone;
        overlay.answer = nil;
        overlay.chainStep = nil;
        [roots addObject:overlay.root];
    }
    [ANNOverlays removeAllObjects];
    if (roots.count > 0) {
        [ANNSystemUI setNeedsUpdate];
    }
    UIWindow *mainWindow = ApexNavigation.mainWindow;
    mainWindow.accessibilityElementsHidden = NO;
    if (keyWent) {
        [mainWindow makeKeyWindow];
    }
    return roots;
}

@end
