#import <React/RCTViewComponentView.h>

NS_ASSUME_NONNULL_BEGIN

/// A slot of the app's React tree (`ANNSlot`). It stays in the hidden host with nothing of its own; its children move
/// into the container with the same slot id (`nativeID`), and lay out at the container's size.
@interface ANNSlotComponentView : RCTViewComponentView
@end

NS_ASSUME_NONNULL_END
