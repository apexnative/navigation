#import "ANNScreenAppearance.h"

NS_ASSUME_NONNULL_BEGIN

/// The part of the record only `ANNAppearanceDispatcher` writes. Main queue.
@interface ANNScreenAppearance ()

@property(nonatomic, readwrite) ANNAppearancePhase phase;

/// The events asked but not sent yet, in this order: `WillAppear`, then `DidAppear`.
@property(nonatomic) BOOL holdsWillAppear;
@property(nonatomic) BOOL holdsDidAppear;

/// The record became ready for good (the mount, `markReady`, the limit).
@property(nonatomic) BOOL readyForGood;

/// Grows each time the hold limit starts or is cancelled: a limit that fires with another number is stale.
@property(nonatomic) NSUInteger holdNumber;

/// Whether the hold limit runs and the content's mount handler is set.
@property(nonatomic) BOOL holding;

/// What runs right after this appearance's `DidAppear` is sent, or when it ends without one
/// (`+[ANNAppearanceDispatcher afterDidAppearOf:run:]`).
@property(nonatomic, strong, nullable) NSMutableArray<dispatch_block_t> *afterDidAppear;

@end

NS_ASSUME_NONNULL_END
