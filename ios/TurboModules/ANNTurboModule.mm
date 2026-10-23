#import "ANNTurboModule.h"
#import "ANNAppDirection.h"
#import "ANNArrangementState.h"
#import "ANNCommandRunner.h"
#import "ANNComponentViewController.h"
#import "ANNDeepLinkQueue.h"
#import "ANNDefaultOptions.h"
#import "ANNErrorHandler.h"
#import "ANNErrorTexts.h"
#import "ANNEventContract.h"
#import "ANNExternalViewController.h"
#import "ANNHinge.h"
#import "ANNLayoutReader.h"
#import "ANNModalCoordinator.h"
#import "ANNNativeScreenCalls.h"
#import "ANNNativeScreenContext+Internal.h"
#import "ANNNodeRegistry.h"
#import "ANNOptionsReader.h"
#import "ANNOptionsRepaint.h"
#import "ANNOverlayCoordinator.h"
#import "ANNPreviewInteraction.h"
#import "ANNPreviewPush.h"
#import "ANNRootCoordinator.h"
#import "ANNScreenInsets.h"
#import "ANNScreenRegions.h"
#import "ANNSlotHost.h"
#import "ANNSplitViewCommands.h"
#import "ANNSystemUI.h"
#import "ANNStackCommands.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

// How many modules this process made: the first one is a cold start, the next ones come with reloads of JavaScript.
static NSUInteger ANNTurboModuleCount;

// What a command could not do, for the text of a failure (`ANNErrorTexts couldNot:reason:`).
static NSString *ANNWhat(NSString *format, ...) NS_FORMAT_FUNCTION(1, 2);

static NSString *ANNWhat(NSString *format, ...) {
    va_list arguments;
    va_start(arguments, format);
    NSString *what = [[NSString alloc] initWithFormat:format arguments:arguments];
    va_end(arguments);
    return what;
}

// The id of a node of the JSON, before it was read: for the text of a failure.
static NSString *ANNRawIdOf(id json) {
    id nodeId = [json isKindOfClass:NSDictionary.class] ? ((NSDictionary *)json)[@"id"] : nil;
    return [nodeId isKindOfClass:NSString.class] ? nodeId : @"";
}

// The size of the logo of the native splash that the CLI `splash` writes into the Info.plist (`getStartInfo`):
// `ApexNavigationSplashLogo` with both numbers above 0, else null.
static id ANNSplashLogo(void) {
    NSDictionary *logo =
        [NSBundle.mainBundle objectForInfoDictionaryKey:@"ApexNavigationSplashLogo"];
    if (![logo isKindOfClass:NSDictionary.class]) {
        return NSNull.null;
    }
    id width = logo[@"width"];
    id height = logo[@"height"];
    if (![width isKindOfClass:NSNumber.class] || ![height isKindOfClass:NSNumber.class] ||
        !([width doubleValue] > 0) || !([height doubleValue] > 0)) {
        return NSNull.null;
    }
    return @{@"width" : width, @"height" : height};
}

// The node of the id for a command: shown, or made and waiting to show; a node of a modal that goes with the old root
// is not found any more (`setRoot` step 3).
static UIViewController *ANNCommandNode(NSString *componentId) {
    UIViewController *node = [ANNNodeRegistry nodeForComponentId:componentId];
    return node && ![ANNModalCoordinator isWithOldRoot:node] ? node : nil;
}

// A command from a screen needs the node of that screen: shown, or made and waiting to show (before the first root no
// screen is).
static BOOL ANNFindsScreen(NSString *componentId, ANNCommandAnswer *answer) {
    if (ANNCommandNode(componentId)) {
        return YES;
    }
    [answer reject:[ANNErrorTexts screenNotShown:componentId]];
    return NO;
}

// Every asynchronous method takes the one path of `ANNCommandRunner`: on the module's queue the layout is read and
// checked whole, then one task on the main queue runs the command with its area's class. A question answers at once on
// the JavaScript thread what it answers before the first data.
@implementation ANNTurboModule {
    BOOL _coldStart;
    id _splashLogo;
}

RCT_EXPORT_MODULE(ANNTurboModule)

+ (BOOL)requiresMainQueueSetup {
    return NO;
}

- (instancetype)init {
    if (self = [super init]) {
        @synchronized(ANNTurboModule.class) {
            _coldStart = ANNTurboModuleCount++ == 0;
        }
        // Fixed when the module is made, as `coldStart` (`getStartInfo`).
        _splashLogo = ANNSplashLogo();
    }
    return self;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeANNTurboModuleSpecJSI>(params);
}

#pragma mark - Root, options

- (void)setRoot:(NSString *)commandId
         layout:(NSDictionary *)layout
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject {
    id rootJson = [layout isKindOfClass:NSDictionary.class] ? layout[@"root"] : nil;
    NSString *what = ANNWhat(@"show the root '%@'", ANNRawIdOf(rootJson));
    [ANNCommandRunner run:@"setRoot"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:^id(NSString **error) {
                      return [ANNLayoutReader rootFrom:layout error:error];
                    }
                     body:^(id root, ANNCommandAnswer *answer) {
                       [ANNRootCoordinator setRoot:root answer:answer];
                     }];
}

// `startHost`: the hidden host of the app's one React tree, once per process (the module keeps it); nothing before the
// navigation has a host. React Native starts its surface once the bundle ran.
- (void)startHost {
    [ANNCommandRunner runVoid:@"startHost"
                         what:@"start the host of the slots"
                         body:^{
                           RCTHost *host = [ApexNavigation getHost];
                           if (host) {
                               [ANNSlotHost startWithHost:host];
                           }
                         }];
}

// `setDefaultOptions`: the whole tree replaces the defaults, unless a value of it can't be read (the previous defaults
// stay); then the window and what shows take them.
- (void)setDefaultOptions:(NSDictionary *)options {
    [ANNCommandRunner runVoid:@"setDefaultOptions"
                         what:@"set the default options"
                         body:^{
                           NSString *unreadablePath = nil;
                           ANNNavigationOptions *defaults =
                               [ANNOptionsReader optionsFrom:options unreadablePath:&unreadablePath];
                           if (unreadablePath) {
                               NSString *text = [ANNErrorTexts unreadableDefaultOption:unreadablePath];
                               [ANNCommandRunner logFailure:@"setDefaultOptions" text:text];
                               return;
                           }
                           [ANNDefaultOptions setCurrent:defaults];
                           // The app's direction; UIKit is asked nothing else: its next questions read the new
                           // defaults.
                           [ANNAppDirection applyDefaultOptionsDirection];
                           [ANNOptionsRepaint applyNewDefaultOptions:defaults];
                         }];
}

// `mergeOptions`: without the navigation's window it logs `nowhereToShow` (JavaScript keeps the options for the next
// root); a node nobody has (not made yet, gone, a reload) or a node of a modal that goes with the old root takes
// nothing, without a log, as on Android. The values that could be read merge even when one couldn't. The whole apply
// is one Core Animation transaction, closed on any error. Then the modal or the overlay of the node takes the merge
// once (`mergeOptions` step 5).
- (void)mergeOptions:(NSString *)componentId options:(NSDictionary *)options {
    NSString *what = ANNWhat(@"merge the options of the screen '%@'", componentId);
    [ANNCommandRunner runVoid:@"mergeOptions"
                         what:what
                         body:^{
                           if (!ApexNavigation.mainWindow) {
                               [ANNCommandRunner logFailure:@"mergeOptions"
                                                       text:ANNErrorTexts.nowhereToShow];
                               return;
                           }
                           UIViewController *node = ANNCommandNode(componentId);
                           if (!node) {
                               return;
                           }
                           NSString *unreadablePath = nil;
                           ANNNavigationOptions *merge =
                               [ANNOptionsReader optionsFrom:options unreadablePath:&unreadablePath];
                           if (unreadablePath) {
                               NSString *text = [ANNErrorTexts unreadableOption:unreadablePath
                                                                    componentId:componentId];
                               [ANNCommandRunner logFailure:@"mergeOptions" text:text];
                           }
                           [CATransaction begin];
                           @try {
                               [node mergeOptions:merge];
                           } @finally {
                               [CATransaction commit];
                           }
                           [ANNModalCoordinator optionsMerged:merge into:node];
                           [ANNOverlayCoordinator optionsMerged:merge into:node];
                           // The groups the merge names are asked again, whatever the node.
                           [ANNSystemUI optionsMerged:merge];
                           // New `keyCommands` of the node or of a parent: the screens it shows try the first
                           // responder again, the first that can wins.
                           NSArray<UIViewController *> *screens =
                               merge.keyCommands ? node.shownComponentViewControllers : @[];
                           for (UIViewController *screen in screens) {
                               if ([screen isKindOfClass:ANNComponentViewController.class]) {
                                   [(ANNComponentViewController *)screen takeKeyCommandsFocus];
                               }
                           }
                         }];
}

#pragma mark - Stack

- (void)push:(NSString *)commandId
    componentId:(NSString *)componentId
         layout:(NSDictionary *)layout
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject {
    NSString *what = ANNWhat(@"push the screen '%@'", ANNRawIdOf(layout));
    [ANNCommandRunner run:@"push"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:^id(NSString **error) {
                      return [ANNLayoutReader nodeFrom:layout error:error];
                    }
                     body:^(id node, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNStackCommands push:node
                                      componentId:componentId
                                             what:what
                                           answer:answer];
                       }
                     }];
}

// iOS lays out no screen before its push: always null (`NSNull`; nil would reach JavaScript as `undefined`).
- (void)preparePush:(NSString *)commandId
        componentId:(NSString *)componentId
             layout:(NSDictionary *)layout
            resolve:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject {
    [ANNErrorHandler resolve:resolve of:@"preparePush" value:NSNull.null];
}

- (void)cancelPreparedPush:(NSString *)componentId {
    NSString *what = ANNWhat(@"cancelPreparedPush for the screen '%@'", componentId);
    [ANNCommandRunner runVoid:@"cancelPreparedPush"
                         what:what
                         body:^{
                         }];
}

- (void)pop:(NSString *)commandId
    componentId:(NSString *)componentId
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject {
    NSString *what = ANNWhat(@"pop the screen '%@'", componentId);
    [ANNCommandRunner run:@"pop"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNStackCommands pop:componentId what:what answer:answer];
                       }
                     }];
}

- (void)popTo:(NSString *)commandId
    componentId:(NSString *)componentId
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject {
    NSString *what = ANNWhat(@"pop to the screen '%@'", componentId);
    [ANNCommandRunner run:@"popTo"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNStackCommands popTo:componentId what:what answer:answer];
                       }
                     }];
}

- (void)popToRoot:(NSString *)commandId
      componentId:(NSString *)componentId
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject {
    NSString *what = ANNWhat(@"pop the stack of the screen '%@'", componentId);
    [ANNCommandRunner run:@"popToRoot"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNStackCommands popToRoot:componentId what:what answer:answer];
                       }
                     }];
}

// The screens may be none here; the command rejects that with the id of its screen, on the module's queue too.
- (void)setStackRoot:(NSString *)commandId
         componentId:(NSString *)componentId
              layout:(NSArray *)layout
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject {
    NSString *what = ANNWhat(@"set the stack of the screen '%@'", componentId);
    [ANNCommandRunner run:@"setStackRoot"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:^id(NSString **error) {
                      NSArray<ANNLayoutNode *> *nodes = [ANNLayoutReader nodesFrom:layout
                                                                             error:error];
                      if (nodes && nodes.count == 0) {
                          *error = [ANNErrorTexts noScreensForStack:componentId];
                          return nil;
                      }
                      return nodes;
                    }
                     body:^(id nodes, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNStackCommands setStackRoot:nodes
                                              componentId:componentId
                                                     what:what
                                                   answer:answer];
                       }
                     }];
}

// Android only: iOS has no back listeners of JavaScript.
- (void)setBackListeners:(BOOL)active {
    [ANNCommandRunner runVoid:@"setBackListeners"
                         what:@"setBackListeners"
                         body:^{
                         }];
}

#pragma mark - Modals, overlays

- (void)showModal:(NSString *)commandId
           layout:(NSDictionary *)layout
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject {
    [self runShow:@"showModal"
        commandId:commandId
           layout:layout
             what:ANNWhat(@"show the screen '%@'", ANNRawIdOf(layout))
          resolve:resolve
           reject:reject];
}

// The lookup is the modals' own: the ids of a modal closed below or by the user are known after its nodes went. The
// options are read on the main queue.
- (void)dismissModal:(NSString *)commandId
         componentId:(NSString *)componentId
             options:(NSDictionary *)options
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject {
    NSDictionary *modalOptions = [options isKindOfClass:NSDictionary.class] ? options : nil;
    [ANNCommandRunner run:@"dismissModal"
                commandId:commandId
                     what:ANNWhat(@"dismiss the modal of the screen '%@'", componentId)
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       [ANNModalCoordinator dismissModal:componentId
                                                 options:modalOptions
                                                  answer:answer];
                     }];
}

- (void)dismissAllModals:(NSString *)commandId
                 options:(NSDictionary *)options
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject {
    NSDictionary *modalOptions = [options isKindOfClass:NSDictionary.class] ? options : nil;
    [ANNCommandRunner run:@"dismissAllModals"
                commandId:commandId
                     what:@"dismiss all modals"
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       [ANNModalCoordinator dismissAllModals:modalOptions answer:answer];
                     }];
}

- (void)showOverlay:(NSString *)commandId
             layout:(NSDictionary *)layout
            resolve:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject {
    [self runShow:@"showOverlay"
        commandId:commandId
           layout:layout
             what:ANNWhat(@"show the screen '%@'", ANNRawIdOf(layout))
          resolve:resolve
           reject:reject];
}

- (void)dismissOverlay:(NSString *)commandId
           componentId:(NSString *)componentId
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject {
    [ANNCommandRunner run:@"dismissOverlay"
                commandId:commandId
                     what:ANNWhat(@"dismiss the overlay of the screen '%@'", componentId)
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       [ANNOverlayCoordinator dismissOverlay:componentId answer:answer];
                     }];
}

- (void)dismissAllOverlays:(NSString *)commandId
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject {
    [ANNCommandRunner run:@"dismissAllOverlays"
                commandId:commandId
                     what:@"dismiss all overlays"
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       [ANNOverlayCoordinator dismissAllOverlays:answer];
                     }];
}

#pragma mark - Split view

- (void)showInColumn:(NSString *)commandId
         componentId:(NSString *)componentId
              column:(NSString *)column
              layout:(NSDictionary *)layout
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject {
    NSString *what =
        ANNWhat(@"show the screen '%@' in the '%@' column", ANNRawIdOf(layout), column);
    [ANNCommandRunner run:@"showInColumn"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:^id(NSString **error) {
                      return [ANNLayoutReader nodeFrom:layout error:error];
                    }
                     body:^(id node, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNSplitViewCommands showInColumn:column
                                                       layout:node
                                                  componentId:componentId
                                                         what:what
                                                       answer:answer];
                       }
                     }];
}

- (void)setColumnShown:(NSString *)commandId
           componentId:(NSString *)componentId
                column:(NSString *)column
                 shown:(BOOL)shown
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject {
    NSString *what = ANNWhat(@"%@ the '%@' column of the screen '%@'", shown ? @"show" : @"hide",
                             column, componentId);
    [ANNCommandRunner run:@"setColumnShown"
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       if (ANNFindsScreen(componentId, answer)) {
                           [ANNSplitViewCommands setColumnShown:column
                                                          shown:shown
                                                    componentId:componentId
                                                         answer:answer];
                       }
                     }];
}

#pragma mark - Questions answered at once

// The stores are written on the main queue and read here under their own locks: JavaScript reads them during a
// screen's first render, so the answer never waits for the main queue.
- (NSDictionary *)getArrangementState:(NSString *)componentId {
    return [ANNArrangementState stateForScreenId:componentId] ?: @{};
}

- (NSDictionary *)getScreenInsets:(NSString *)componentId {
    return [ANNScreenInsets insetsForScreenId:componentId] ?: @{};
}

- (NSDictionary *)getScreenRegions:(NSString *)componentId {
    NSDictionary *regions = [ANNScreenRegions regionsForScreenId:componentId];
    const auto &keys = ANNEventContract.ScreenRegionsChanged;
    return regions ?: @{keys.division : @[], keys.occlusion : @[]};
}

// `setHingeUpdates`: JavaScript counts its `useHinge` hooks; the events go with the emitter of the JavaScript that runs
// now.
- (void)setHingeUpdates:(BOOL)enabled {
    [ANNCommandRunner runVoid:@"setHingeUpdates"
                         what:@"setHingeUpdates"
                         body:^{
                           ANNEventEmitter *eventEmitter = ApexNavigation.eventEmitter;
                           [ANNHinge setUpdatesEnabled:enabled eventEmitter:eventEmitter];
                         }];
}

- (NSDictionary *)getHinge {
    return ANNHinge.lastValue;
}

- (NSDictionary *)getStartInfo {
    return @{
        @"coldStart" : @(_coldStart),
        @"splashLogo" : _splashLogo ?: NSNull.null,
        @"arrangements" : @(ANNArrangementState.canShowArrangements),
    };
}

#pragma mark - Links, native screens, previews

- (void)getLaunchLink:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [ANNCommandRunner run:@"getLaunchLink"
                commandId:nil
                     what:@"read the launch link"
                  resolve:resolve
                   reject:reject
                    check:nil
                     body:^(id layout, ANNCommandAnswer *answer) {
                       // The links module answers once the launch links are known, at once in the background
                       // (`getLaunchLink`): the URL, or null (`NSNull`; nil would reach JavaScript as `undefined`).
                       [ANNDeepLinkQueue takeLaunchLink:^(NSURL *url) {
                         [answer resolve:url.absoluteString ?: (id)NSNull.null];
                       }];
                     }];
}

- (void)updateNativeScreenProps:(NSString *)componentId update:(NSDictionary *)update {
    NSString *what = ANNWhat(@"updateNativeScreenProps for the screen '%@'", componentId);
    // At once, also during a transition; a node that isn't a native screen, no node, or a node of a modal that goes
    // with the old root takes nothing, without a log, as on Android.
    [ANNCommandRunner runVoid:@"updateNativeScreenProps"
                         what:what
                         body:^{
                           UIViewController *node = ANNCommandNode(componentId);
                           if ([node isKindOfClass:ANNExternalViewController.class]) {
                               [((ANNExternalViewController *)node).nativeScreenContext
                                   applyUpdate:update];
                           }
                         }];
}

- (void)resolveNativeScreenCall:(NSString *)callId result:(NSDictionary *)result {
    // An unknown or finished call does nothing (`resolveNativeScreenCall`).
    [ANNCommandRunner runVoid:@"resolveNativeScreenCall"
                         what:@"resolveNativeScreenCall"
                         body:^{
                           [ANNNativeScreenCalls resolveCall:callId result:result];
                         }];
}

// `dismissPreview`: the attached preview of that screen goes, else one that still looks for its view; otherwise (a tap
// opened it, it went already, an unknown id) nothing.
- (void)dismissPreview:(NSString *)previewId {
    NSString *what = ANNWhat(@"dismissPreview for the screen '%@'", previewId);
    [ANNCommandRunner runVoid:@"dismissPreview"
                         what:what
                         body:^{
                           if (![ANNPreviewInteraction dismissPreviewOfScreen:previewId]) {
                               [ANNPreviewPush dismissSearchingPreview:previewId];
                           }
                         }];
}

#pragma mark - The shared shapes

// A command that shows a layout of its own, over what shows: a modal or an overlay.
- (void)runShow:(NSString *)name
      commandId:(NSString *)commandId
         layout:(NSDictionary *)layout
           what:(NSString *)what
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject {
    [ANNCommandRunner run:name
                commandId:commandId
                     what:what
                  resolve:resolve
                   reject:reject
                    check:^id(NSString **error) {
                      return [ANNLayoutReader nodeFrom:layout error:error];
                    }
                     body:^(id node, ANNCommandAnswer *answer) {
                       if ([name isEqualToString:@"showModal"]) {
                           [ANNModalCoordinator showModal:node answer:answer];
                       } else {
                           [ANNOverlayCoordinator showOverlay:node answer:answer];
                       }
                     }];
}

@end
