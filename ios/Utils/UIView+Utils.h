#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface UIView (Utils)

/// The first view of the class in the subviews of this view, at any depth, depth first; nil without one.
- (nullable UIView *)findDescendantByClass:(nullable Class)viewClass;

@end

NS_ASSUME_NONNULL_END
