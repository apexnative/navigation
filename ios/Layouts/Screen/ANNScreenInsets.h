#import "ANNEventEmitter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Posted on the main queue when the padding of a screen's safe area view changed; the object is the screen id.
extern NSNotificationName const ANNScreenSafeAreaPaddingDidChangeNotification;

/// The safe area of each screen: how much of its content the bars and the system cover on each side. The padding is
/// the part of it the screen's safe area view keeps its content out of: the insets on the edges the screen's options
/// give. JavaScript gets the rest, what still covers the content (0 on a padded edge): it reads it from its own thread
/// for a screen's first render, and gets every change as `ANN.ScreenInsetsChanged`. Main queue, but the reads.
@interface ANNScreenInsets : NSObject

/// Keeps the screen's insets and padding, and tells when they changed.
+ (void)setInsets:(UIEdgeInsets)insets
          padding:(UIEdgeInsets)padding
      forScreenId:(NSString *)screenId
     eventEmitter:(ANNEventEmitter *)eventEmitter;
/// The padding of the screen's safe area view; none before the screen was laid out. Main queue.
+ (UIEdgeInsets)paddingForScreenId:(NSString *)screenId;
/// `{ top, right, bottom, left }`, or nil before the screen was laid out. Any thread.
+ (nullable NSDictionary<NSString *, NSNumber *> *)insetsForScreenId:(NSString *)screenId;
+ (void)removeScreenId:(NSString *)screenId;
/// Forgets every screen: after a reload the new JavaScript counts its ids anew. Main queue.
+ (void)removeAll;

@end

NS_ASSUME_NONNULL_END
