#import "ANNPreviewPush.h"
#import "ANNAssert.h"
#import "ANNErrorTexts.h"
#import "ANNLayoutFactory.h"
#import "ANNParking.h"
#import "ANNPopoverPresenter.h"
#import "ANNPreviewInteraction.h"
#import "ANNReloadCoordinator.h"
#import "ANNSlotScreenView.h"
#import "ANNStackCommands.h"
#import "ANNStackController.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

// The view of a component mounts a frame or so after the command that rendered it: it is looked for at each mount of
// the source's content until this limit (30 frames), then the preview goes, as with a `nativeID` no view has.
static const NSTimeInterval ANNPreviewSearchLimit = 30.0 / 60;
// Where the source's mounts can't be heard, a frame apart.
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
// The content whose mounts the search hears, while it looks.
@property(nonatomic, weak, nullable) UIView<ANNComponentProtocol> *watched;
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
    [self startSearch:record];
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

// The view is looked for at once; else again at each mount of the source's content, where it comes with one of them,
// and a last time at the limit, when the preview goes. Both later steps keep the generation they were made with: after
// a reload they do nothing.
+ (void)startSearch:(ANNPreviewRecord *)record {
    if ([self search:record last:NO]) {
        return;
    }
    UIViewController *source = record.source;
    // A source that is a container (a stack) is heard through the screen it shows.
    UIView<ANNComponentProtocol> *content =
        source.reactView ?: source.presentedComponentViewController.reactView;
    __weak ANNPreviewRecord *weakRecord = record;
    NSUInteger generation = record.generation;
    // A slot screen hears only the first mount of its slot, not those deeper in it, and a container may show no React
    // content yet: there the view is looked for on each frame until the limit.
    if (!content || [content isKindOfClass:ANNSlotScreenView.class]) {
        [self searchNextFrame:record];
    } else {
        record.watched = content;
        [content setContentDidMount:^{
          ANNPreviewRecord *searching = weakRecord;
          if (searching && generation == ANNReloadCoordinator.generation) {
              [ANNPreviewPush search:searching last:NO];
          }
        }
                             forKey:[self mountKeyOf:record]];
    }
    int64_t delay = (int64_t)(ANNPreviewSearchLimit * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      ANNPreviewRecord *searching = weakRecord;
      if (searching && generation == ANNReloadCoordinator.generation) {
          [ANNPreviewPush search:searching last:YES];
      }
    });
}

// One more search on the next frame, and so on until the view is found or the preview goes; after a reload, none.
+ (void)searchNextFrame:(ANNPreviewRecord *)record {
    __weak ANNPreviewRecord *weakRecord = record;
    NSUInteger generation = record.generation;
    int64_t delay = (int64_t)(ANNPreviewSearchInterval * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      ANNPreviewRecord *searching = weakRecord;
      if (searching && generation == ANNReloadCoordinator.generation &&
          ![ANNPreviewPush search:searching last:NO]) {
          [ANNPreviewPush searchNextFrame:searching];
      }
    });
}

// YES when the search is over: the view was found, or the preview went or stopped. Each search asks first whether the
// preview still lives: a source that went makes it go, a reload stops it silently. The last one, not finding the view,
// makes the preview go.
+ (BOOL)search:(ANNPreviewRecord *)record last:(BOOL)last {
    if (record.done || record.attached) {
        return YES;
    }
    if (record.generation != ANNReloadCoordinator.generation) {
        return YES;
    }
    UIViewController *source = record.source;
    if (!source || source.nodeHelper.destroyed) {
        [self gone:record];
        return YES;
    }
    // Read only: the view is found by its `nativeID`, and its tag is its current Fabric tag.
    UIView *view = [ANNPopoverPresenter viewWithNativeID:record.nativeID in:source.viewIfLoaded];
    if (view) {
        [self attach:record toView:view source:source];
        return YES;
    }
    if (last) {
        [self gone:record];
        return YES;
    }
    return NO;
}

// The key of the search's mount handler: one per preview, so that two previews of one source don't replace each other.
+ (NSString *)mountKeyOf:(ANNPreviewRecord *)record {
    return [@"preview." stringByAppendingString:record.previewId];
}

// The search stops hearing the mounts: only its own handler goes.
+ (void)stopWatching:(ANNPreviewRecord *)record {
    UIView<ANNComponentProtocol> *content = record.watched;
    record.watched = nil;
    [content setContentDidMount:nil forKey:[self mountKeyOf:record]];
}

+ (void)attach:(ANNPreviewRecord *)record toView:(UIView *)view source:(UIViewController *)source {
    record.attached = YES;
    [self stopWatching:record];
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

// The record is done: the search stops, the screen leaves its unseen place, the table forgets it.
+ (void)finish:(ANNPreviewRecord *)record {
    record.done = YES;
    [self stopWatching:record];
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
        [self stopWatching:record];
        [self unpark:record];
        if (!record.attached) {
            [screens addObject:record.screen];
        }
    }
    [ANNPreviewRecords() removeAllObjects];
    return screens;
}

@end
