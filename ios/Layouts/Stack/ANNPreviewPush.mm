#import "ANNPreviewPush.h"
#import "ANNAssert.h"
#import "ANNErrorTexts.h"
#import "ANNLayoutFactory.h"
#import "ANNParking.h"
#import "ANNPopoverPresenter.h"
#import "ANNPreviewInteraction.h"
#import "ANNReloadCoordinator.h"
#import "ANNStackCommands.h"
#import "ANNStackController.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

// The view of a component mounts a frame or so after the command that rendered it: it is looked for this many times,
// a frame apart.
static const NSUInteger ANNPreviewSearchTries = 30;
static const NSTimeInterval ANNPreviewSearchInterval = 1.0 / 60;

// One preview from its push to its end: the tap took it, or it went.
@interface ANNPreviewRecord : NSObject
@property(nonatomic, copy) NSString *previewId;
// Held strongly while the preview lives: the registry holds waiting nodes weakly.
@property(nonatomic, strong) UIViewController *screen;
@property(nonatomic, weak, nullable) UIViewController *source;
@property(nonatomic, strong) ANNCommandAnswer *answer;
@property(nonatomic, copy, nullable) NSString *key;
@property(nonatomic, copy) NSString *nativeID;
@property(nonatomic, copy) NSString *componentId;
@property(nonatomic, copy) NSString *what;
@property(nonatomic) NSUInteger generation;
@property(nonatomic) NSUInteger tries;
@property(nonatomic) BOOL attached;
@property(nonatomic) BOOL done;
// The screen's unseen place in the main window, at its preview size, until UIKit takes its view for the menu.
@property(nonatomic, strong, nullable) UIView *host;
@property(nonatomic, strong, nullable) ANNParking *parking;
@end

@implementation ANNPreviewRecord
@end

// The previews that live, by the id of their screen. Main queue.
static NSMutableDictionary<NSString *, ANNPreviewRecord *> *ANNPreviewRecords(void) {
    static NSMutableDictionary<NSString *, ANNPreviewRecord *> *records;
    if (!records) {
        records = [NSMutableDictionary new];
    }
    return records;
}

@implementation ANNPreviewPush

+ (void)push:(ANNLayoutNode *)layout
        options:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById
         source:(UIViewController *)source
    componentId:(NSString *)componentId
           what:(NSString *)what
         answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    ANNPreviewOptions *preview = optionsById[layout.nodeId].preview;
    // Registered as it is made: commands find it by its id while it waits.
    UIViewController *screen = [ANNLayoutFactory nodeFromLayout:layout options:optionsById];
    // Each missing size is the source screen's.
    CGSize sourceSize = source.view.bounds.size;
    CGSize size = CGSizeMake(preview.width.hasValue ? [preview.width get] : sourceSize.width,
                             preview.height.hasValue ? [preview.height get] : sourceSize.height);
    screen.preferredContentSize = size;
    ANNPreviewRecord *record = [ANNPreviewRecord new];
    record.previewId = layout.nodeId;
    record.screen = screen;
    record.source = source;
    record.answer = answer;
    // The key applies only once the tap made the preview a screen of the stack.
    record.key = ANNStackKeyOfLayout(layout);
    record.nativeID = [preview.nativeID get] ?: @"";
    record.componentId = componentId;
    record.what = what;
    record.generation = answer.generation;
    ANNPreviewRecords()[record.previewId] = record;
    @try {
        [self parkAndRender:record size:size];
    } @catch (NSException *exception) {
        [self finish:record];
        [screen destroy];
        @throw;
    }
    [self search:record];
}

// The screen renders as now: its view waits unseen in the main window, at the size of the preview, so that its content
// lays out; nothing waits for it.
+ (void)parkAndRender:(ANNPreviewRecord *)record size:(CGSize)size {
    UIWindow *window = ApexNavigation.mainWindow;
    if (window) {
        UIView *host = [[UIView alloc] initWithFrame:CGRectMake(0, 0, size.width, size.height)];
        host.userInteractionEnabled = NO;
        host.accessibilityElementsHidden = YES;
        host.clipsToBounds = YES;
        [window insertSubview:host atIndex:0];
        record.host = host;
        // The record knows its parking only once the park is done: UIKit reports the park's own appearance from
        // inside it (the view without its superview yet), which `screenWillAppear:` must leave alone; the parking
        // drives the screen's appearance, so nothing reaches JavaScript.
        ANNParking *parking = [ANNParking new];
        [parking park:record.screen inView:host];
        record.parking = parking;
    }
    [record.screen render];
}

#pragma mark - The search

// Each try asks first whether the preview still lives: a source that went makes it go, a reload stops it silently.
+ (void)search:(ANNPreviewRecord *)record {
    if (record.done || record.attached) {
        return;
    }
    if (record.generation != ANNReloadCoordinator.generation) {
        return;
    }
    UIViewController *source = record.source;
    if (!source || source.nodeHelper.destroyed) {
        [self gone:record];
        return;
    }
    // Read only: the view is found by its `nativeID`, and its tag is its current Fabric tag.
    UIView *view = [ANNPopoverPresenter viewWithNativeID:record.nativeID in:source.viewIfLoaded];
    if (view) {
        [self attach:record toView:view source:source];
        return;
    }
    if (++record.tries >= ANNPreviewSearchTries) {
        [self gone:record];
        return;
    }
    __weak ANNPreviewRecord *weakRecord = record;
    int64_t delay = (int64_t)(ANNPreviewSearchInterval * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      ANNPreviewRecord *searching = weakRecord;
      if (searching) {
          [ANNPreviewPush search:searching];
      }
    });
}

+ (void)attach:(ANNPreviewRecord *)record toView:(UIView *)view source:(UIViewController *)source {
    record.attached = YES;
    __weak ANNPreviewRecord *weakRecord = record;
    BOOL (^commit)(void) = ^BOOL {
      return [ANNPreviewPush commit:weakRecord];
    };
    void (^dismissed)(void) = ^{
      [ANNPreviewPush gone:weakRecord];
    };
    [ANNPreviewInteraction attachPreview:record.screen
                                  toView:view
                                reactTag:view.tag
                                  source:source
                                  commit:commit
                               dismissed:dismissed];
}

#pragma mark - The tap

// YES once the core took the preview: a key that leads back to an entry, or the push the queue accepted. From then on
// the preview's record is done, and its going does nothing. NO only when nothing was done: no `preview.commit`, or the
// source is no longer in a stack.
+ (BOOL)commit:(ANNPreviewRecord *)record {
    if (!record || record.done || !record.answer.isCurrent) {
        return NO;
    }
    UIViewController *screen = record.screen;
    UIViewController *source = record.source;
    BOOL sourceLives = source && !source.nodeHelper.destroyed;
    ANNEventEmitter *eventEmitter = sourceLives ? source.eventEmitter : screen.eventEmitter;
    [eventEmitter sendPreviewCompleted:record.componentId previewComponentId:record.previewId];
    if (![[screen resolveOptionsWithDefault].preview.commit withDefault:NO] || !sourceLives) {
        return NO;
    }
    ANNStackController *stack = [ANNStackCommands stackOf:source];
    UIViewController *entry = stack && source != stack ? [stack entryOf:source] : nil;
    BOOL entryInStack =
        entry && [stack.queue.entries indexOfObjectIdenticalTo:entry] != NSNotFound;
    BOOL inStack = stack && (source == stack || entryInStack);
    if (!inStack) {
        return NO;
    }
    [self finish:record];
    // The key of the preview names an entry of the stack: back to it, as a push's key does.
    NSString *key = record.key;
    UIViewController *keyed = key ? [stack entryWithKey:key] : nil;
    if (keyed) {
        [screen destroy];
        NSString *value = ANNStackAnswerIdOf(keyed, ANNStackAnswerCommandKey);
        if (keyed == stack.queue.entries.lastObject) {
            [record.answer resolve:value];
            return YES;
        }
        [stack.queue cutTo:keyed
                    answer:record.answer
                     value:value
                      what:record.what
               componentId:record.componentId
                    source:entry
                    silent:NO];
        return YES;
    }
    // The preview grows into the screen: a push without an animation, and without a wait, as the screen rendered
    // already; its answer at the end of the transaction.
    [stack setKey:key ofEntry:screen];
    [stack.queue push:screen
             answer:record.answer
               what:record.what
        componentId:record.componentId
             source:entry
           animated:NO
              waits:NO];
    return YES;
}

#pragma mark - The end

// The preview went without being opened: once, its screen is destroyed (its React root stops first) and the push
// rejects.
+ (void)gone:(ANNPreviewRecord *)record {
    if (!record || record.done) {
        return;
    }
    [self finish:record];
    [record.screen destroy];
    [record.answer reject:[ANNErrorTexts previewGone:record.previewId]];
}

// The record is done: the screen leaves its unseen place, the table forgets it.
+ (void)finish:(ANNPreviewRecord *)record {
    record.done = YES;
    [self unpark:record];
    if (ANNPreviewRecords()[record.previewId] == record) {
        [ANNPreviewRecords() removeObjectForKey:record.previewId];
    }
}

+ (void)unpark:(ANNPreviewRecord *)record {
    [record.parking unpark:record.screen endingDeparture:NO];
    record.parking = nil;
    [record.host removeFromSuperview];
    record.host = nil;
}

+ (BOOL)dismissSearchingPreview:(NSString *)previewId {
    ANNAssertMainQueue();
    ANNPreviewRecord *record = ANNPreviewRecords()[previewId];
    if (!record || record.attached || record.done) {
        return NO;
    }
    [self gone:record];
    return YES;
}

+ (BOOL)isPreviewScreen:(UIViewController *)screen {
    NSMutableDictionary<NSString *, ANNPreviewRecord *> *records = ANNPreviewRecords();
    NSString *previewId = records.count > 0 ? screen.layoutInfo.componentId : nil;
    return previewId && records[previewId].screen == screen;
}

+ (void)screenWillAppear:(UIViewController *)screen {
    NSMutableDictionary<NSString *, ANNPreviewRecord *> *records = ANNPreviewRecords();
    if (records.count == 0) {
        return;
    }
    NSString *previewId = screen.layoutInfo.componentId;
    ANNPreviewRecord *record = previewId ? records[previewId] : nil;
    if (!record || record.screen != screen || !record.parking) {
        return;
    }
    // Still in its place: the place itself made it appear (UIKit tells a view controller without a parent so).
    UIView *view = screen.viewIfLoaded;
    if (view.superview == record.host) {
        return;
    }
    [self unpark:record];
}

+ (NSArray<UIViewController *> *)takeSearchingForReload {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *screens = [NSMutableArray new];
    for (ANNPreviewRecord *record in ANNPreviewRecords().allValues) {
        record.done = YES;
        [self unpark:record];
        if (!record.attached) {
            [screens addObject:record.screen];
        }
    }
    [ANNPreviewRecords() removeAllObjects];
    return screens;
}

@end
