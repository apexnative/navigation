#import "ANNEventEmitter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A view that keeps a screen's content out of its padding (the screen's safe area view).
@protocol ANNScreenPaddingListener <NSObject>
/// The padding of its screen changed. Main queue.
- (void)screenPaddingDidChange;
@end

/// What JavaScript knows of each screen's place, by the screen's id, in one record: its safe area (the insets
/// JavaScript gets, `useSafeAreaInsets`, and the padding of its safe area view), its reserved regions
/// (`useReservedRegions`) and, in a pair, its arrangement (`useArrangement`). The core measures them
/// (`ANNScreenSafeArea`, `ANNScreenRegions`, the pair's layout) on the main queue and keeps them here; JavaScript reads
/// them from its own thread for a screen's first render, under one lock, and gets every change as an event. When the
/// screen goes, `removeScreenId:` clears all of it. As on Android (`ScreenEnvironment`).
@interface ANNScreenEnvironment : NSObject

/// Keeps the screen's insets and the padding of its safe area view. The padding is the part of the insets the safe
/// area view keeps the content out of (the edges the screen's options give); JavaScript gets the rest, what still
/// covers the content (0 on a padded edge). Tells the padding's listeners of that screen when the padding changed, then
/// JavaScript (`ANN.ScreenInsetsChanged`) when its insets did. Main queue.
+ (void)setInsets:(UIEdgeInsets)insets
          padding:(UIEdgeInsets)padding
      forScreenId:(NSString *)screenId
     eventEmitter:(ANNEventEmitter *)eventEmitter;
/// The padding of the screen's safe area view; none before the screen was laid out. Any thread.
+ (UIEdgeInsets)paddingForScreenId:(NSString *)screenId;
/// `{ top, right, bottom, left }`, or nil before the screen was laid out. Any thread.
+ (nullable NSDictionary<NSString *, NSNumber *> *)insetsForScreenId:(NSString *)screenId;

/// Keeps the screen's regions (`{ division, occlusion }`) and tells JavaScript (`ANN.ScreenRegionsChanged`) when they
/// changed. None at first is what JavaScript reads before the first layout: it is neither kept nor told. Main queue.
+ (void)setRegions:(NSDictionary<NSString *, NSArray *> *)regions
       forScreenId:(NSString *)screenId
      eventEmitter:(ANNEventEmitter *)eventEmitter;
/// `{ division, occlusion }`, or nil before the screen's regions were first kept. Any thread.
+ (nullable NSDictionary<NSString *, NSArray *> *)regionsForScreenId:(NSString *)screenId;

/// Keeps the screen's arrangement (`{ placement, layout, front, hidden }`) and tells JavaScript
/// (`ANN.ArrangementChanged`) when it changed; nothing goes without an emitter. Main queue.
+ (void)setArrangement:(NSDictionary<NSString *, id> *)arrangement
           forScreenId:(NSString *)screenId
          eventEmitter:(nullable ANNEventEmitter *)eventEmitter;
/// The screen's arrangement, or nil for a screen outside a pair. Any thread.
+ (nullable NSDictionary<NSString *, id> *)arrangementForScreenId:(NSString *)screenId;

/// Tells `listener` of each change of the screen's padding, until it is removed or goes (held weakly). Several
/// listeners may listen to one screen. Main queue.
+ (void)addPaddingListener:(id<ANNScreenPaddingListener>)listener forScreenId:(NSString *)screenId;
+ (void)removePaddingListener:(id<ANNScreenPaddingListener>)listener
                  forScreenId:(NSString *)screenId;

/// Forgets everything kept for the screen. Its padding listeners stay: a live safe area view keeps listening while it
/// keeps the id, and stops itself. Main queue.
+ (void)removeScreenId:(NSString *)screenId;
/// Forgets every screen: after a reload the new JavaScript counts its ids anew. Main queue.
+ (void)removeAll;

@end

NS_ASSUME_NONNULL_END
