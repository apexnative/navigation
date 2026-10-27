#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What a frame driver moves: a view's properties (`ANNViewTracks`), an element, a shared element's copy. It gives its
/// values for the whole transition at once, as Core Animation's animations on its layers (`ANNKeyframes`); past its end
/// they hold its end values.
@protocol ANNFrameClient <NSObject>

/// The client's animations for a transition of `length` seconds go onto its layers, all inside the driver's
/// transaction, so they start together.
- (void)addAnimationsWithLength:(NSTimeInterval)length;

/// The time from which the client holds its end values.
@property(nonatomic, readonly) NSTimeInterval endTime;

/// The client's animations go, every value it wrote goes back as it was read before the transition (also at a
/// cancel), and a client that made views of its own removes them. Once; later calls do nothing.
- (void)restore;

@end

/// The times a client samples its values at, in seconds from the start: 0, every 1/120 s up to `end` (wider apart for
/// a transition over 20 s, 2400 samples at most), each of `marks` within it (a track's start and end, a swap's), `end`
/// and `length`; ascending, without repeats. The values between two samples are linear, so a kink of a track falls on
/// a sample.
FOUNDATION_EXPORT NSArray<NSNumber *> *ANNSampleTimes(NSTimeInterval end, NSTimeInterval length,
                                                      NSArray<NSNumber *> *marks);

/// The animation of `keyPath` through `values` at `times` (from `ANNSampleTimes`) over `length` seconds: linear
/// between them, holding the first value before and the last after (until it is removed). Nil for no length, and for
/// values and times that don't pair up.
FOUNDATION_EXPORT CAKeyframeAnimation *_Nullable ANNKeyframes(NSString *keyPath, NSArray *values,
                                                              NSArray<NSNumber *> *times,
                                                              NSTimeInterval length);

/// The key of the core's animation of `keyPath` on a layer, for its `restore`.
FOUNDATION_EXPORT NSString *ANNAnimationKey(NSString *keyPath);

/// The frames of one running transition, in Core Animation: every client adds its animations in one transaction, which
/// starts them together at its commit; the render server plays them, so a busy main queue no longer stops the
/// transition. Once every animation ended (or was removed) and `length` passed, `done` runs once on the main queue.
/// Main queue.
@interface ANNFrameDriver : NSObject

- (instancetype)initWithClients:(NSArray<id<ANNFrameClient>> *)clients
                         length:(NSTimeInterval)length NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The clients add their animations, which start at the commit of the current transaction (the first frame shows the
/// start values). `done` comes later, never inside this call.
- (void)startWithDone:(dispatch_block_t)done;

/// The transition stops (a destroy, a reload, a newer `setRoot`): `done` never runs. The caller restores the clients,
/// which removes their animations.
- (void)cancel;

/// The animations run.
@property(nonatomic, readonly) BOOL isRunning;

@end

NS_ASSUME_NONNULL_END
