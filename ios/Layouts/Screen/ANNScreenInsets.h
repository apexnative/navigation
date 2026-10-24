#import "ANNEventEmitter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A view that keeps a screen's content out of its padding (the screen's safe area view).
@protocol ANNScreenPaddingListener <NSObject>
/// The padding of its screen changed. Main queue.
- (void)screenPaddingDidChange;
@end

/// The safe area of each screen: how much of its content the bars and the system cover on each side. The padding is
/// the part of it the screen's safe area view keeps its content out of: the insets on the edges the screen's options
/// give. JavaScript gets the rest, what still covers the content (0 on a padded edge): it reads it from its own thread
/// for a screen's first render, and gets every change as `ANN.ScreenInsetsChanged`. Main queue, but the reads.
@interface ANNScreenInsets : NSObject

/// Keeps the screen's insets and padding, and tells when they changed: the padding's listeners of that screen only.
+ (void)setInsets:(UIEdgeInsets)insets
          padding:(UIEdgeInsets)padding
      forScreenId:(NSString *)screenId
     eventEmitter:(ANNEventEmitter *)eventEmitter;
/// The padding of the screen's safe area view; none before the screen was laid out. Main queue.
+ (UIEdgeInsets)paddingForScreenId:(NSString *)screenId;
/// `{ top, right, bottom, left }`, or nil before the screen was laid out. Any thread.
+ (nullable NSDictionary<NSString *, NSNumber *> *)insetsForScreenId:(NSString *)screenId;
/// Tells `listener` of each change of the screen's padding, until it is removed or goes (held weakly). Several listeners
/// may listen to one screen. Main queue.
+ (void)addPaddingListener:(id<ANNScreenPaddingListener>)listener forScreenId:(NSString *)screenId;
+ (void)removePaddingListener:(id<ANNScreenPaddingListener>)listener forScreenId:(NSString *)screenId;
/// Forgets the screen's insets and padding; its listeners stay, as long as their views keep its id.
+ (void)removeScreenId:(NSString *)screenId;
/// Forgets every screen: after a reload the new JavaScript counts its ids anew. Main queue.
+ (void)removeAll;

@end

NS_ASSUME_NONNULL_END
