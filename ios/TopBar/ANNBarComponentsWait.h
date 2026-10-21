#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The limits of a wait for the React components of the bars of some screens, in seconds from the wait's start; 0: no
/// limit of its own (the caller's limit ends it).
typedef struct {
    /// The title and the background (1 s for a push).
    NSTimeInterval titleAndBackground;
    /// The component buttons (0.5 s for a push).
    NSTimeInterval buttons;
} ANNBarWaitLimits;

/// A wait for the React components of the bars of `screens` (title, background, component buttons): each screen makes
/// the roots its resolved options name and doesn't have yet, then the kinds are waited together from the start, each to
/// its own limit; the wait is done when every kind is ready or past its limit. A kind with nothing to wait for (none
/// named, or its own `waitForRender: false`) is ready at once. A wait that ends at a limit marks nothing ready: the
/// roots' own appearance events stay with the dispatcher. Main queue.
@interface ANNBarComponentsWait : NSObject

/// Starts a wait; nil when there is nothing to wait for (`done` is not called then).
+ (nullable instancetype)waitForScreens:(NSArray<UIViewController *> *)screens
                                 limits:(ANNBarWaitLimits)limits
                                   done:(dispatch_block_t)done;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The wait ends without calling `done`.
- (void)cancel;

/// Whether `done` ran or the wait was cancelled.
@property(nonatomic, readonly) BOOL finished;

@end

NS_ASSUME_NONNULL_END
