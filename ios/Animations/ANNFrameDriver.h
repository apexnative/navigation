#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What a frame driver moves: a view's properties (`ANNViewTracks`), an element, a shared element's copy. Each frame it
/// writes its values at the time from the transition's start; past its end it holds its end values.
@protocol ANNFrameClient <NSObject>

/// The values at `time`, in seconds from the start of the transition.
- (void)applyAtTime:(NSTimeInterval)time;

/// The time from which the client holds its end values.
@property(nonatomic, readonly) NSTimeInterval endTime;

/// Every value the client wrote goes back as it was read before the transition (also at a cancel); a client that made
/// views of its own removes them. Once; later calls do nothing.
- (void)restore;

@end

/// The frames of one running transition: one `CADisplayLink` in the common modes of the run loop, so frames go on while
/// a finger drags a scroll view; the time is the link's `targetTimestamp` from the start (monotonic host time). Each
/// frame every client writes its values; once the time reached `length` and every client its end, the clients write
/// their ends, the link stops and `done` runs once. No `CATransaction` waits. Main queue.
@interface ANNFrameDriver : NSObject

- (instancetype)initWithClients:(NSArray<id<ANNFrameClient>> *)clients
                         length:(NSTimeInterval)length NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The clients write their values of time 0 now (the first frame shows the start values), and the frames start. `done`
/// comes on a later frame, never inside this call.
- (void)startWithDone:(dispatch_block_t)done;

/// The transition stops where it is (a destroy, a reload, a newer `setRoot`): the clients write their end values, the
/// link stops, `done` never runs. The caller restores the clients.
- (void)cancel;

/// The frames run.
@property(nonatomic, readonly) BOOL isRunning;

@end

NS_ASSUME_NONNULL_END
