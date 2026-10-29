#import "ANNFrameDriver.h"
#import <UIKit/UIKit.h>

@class ANNSharedElementTransitionOptions;
@class ANNTransitionViews;

NS_ASSUME_NONNULL_BEGIN

/// One shared pair in flight: a plain view of the core, the copy, flies in the transition's container from the `from`
/// view to the `to` view with two pictures of them (`renderInContext:` at the screen's scale). The React views are
/// neither moved nor changed: they are only `hidden` while the copy flies and get back what they had before the
/// transition completes. All geometry is in the container's coordinates. The copy's size, center, visible part (a
/// mask), corner radius and layer transform go from the `from` values to the `to` values on the pair's curve, delay and
/// duration, in Core Animation's animations of the copy's layers.
///
/// A view (not a text): both pictures fill the copy, the copy clips to its corners, the `to` picture fades in over the
/// flight. A text (React's paragraph): the pictures keep their aspect, as tall as the copy, at the line's start (the
/// right in RTL), neither clipped nor masked, and swap linearly in a quarter of the flight — the first when the text
/// grows, the last when it shrinks.
@interface ANNSharedElementFlight : NSObject <ANNFrameClient>

/// The flights of the pairs whose two views are found (`fromId` in the leaving screen, `toId` in the arriving one); the
/// others are skipped and their views stay as they are. A pair found that can't fly — a view without a size, a
/// picture that can't be taken, a place that can't be worked out, an exception in its start (`ANNElementStep`) — is
/// skipped too, with a line in the log. The pictures are taken now, then the React views hide and the copies go into
/// `container` by the `zIndex` of their `to` views, ascending (no `layer.zPosition`).
+ (NSArray<ANNSharedElementFlight *> *)
    flightsOf:(nullable NSArray<ANNSharedElementTransitionOptions *> *)pairs
        views:(ANNTransitionViews *)views
    container:(UIView *)container;

/// Every pair's `toId` is found in the arriving screen (the wait's test).
+ (BOOL)allTargetsFound:(nullable NSArray<ANNSharedElementTransitionOptions *> *)pairs
                  views:(ANNTransitionViews *)views;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The copy that flies (a view of the core in the container).
@property(nonatomic, strong, readonly) UIView *copyView;

/// The pair's two ids, in quotes: its name in the log.
@property(nonatomic, copy, readonly) NSString *name;

@end

NS_ASSUME_NONNULL_END
