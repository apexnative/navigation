#import "ANNReactView.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The place of one tab in the custom row: it holds the tab's content view (a React root by name now; a component of
/// the React tree can take the same place later) at its own size, carries the tab's `testID`, and takes the touches
/// itself: the content takes none. It gives the root its native props as they change (`tabIndex`, `selected`, `badge`,
/// `dot`, `dotColor`).
@interface ANNBottomTabCellView : UIView

- (instancetype)initWithIndex:(NSUInteger)index;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

@property(nonatomic, readonly) NSUInteger index;

/// What the cell shows; a new one replaces the old one in the cell (the roots stop with their tabs, not here).
@property(nonatomic, strong, nullable) UIView<ANNComponentProtocol> *content;

/// The tab's state as the root gets it: set only when something changed.
- (void)setSelected:(BOOL)selected
              badge:(nullable NSString *)badge
                dot:(BOOL)dot
           dotColor:(nullable UIColor *)dotColor;

/// The tab's `testID` on the cell's place; nil removes it.
- (void)setTestID:(nullable NSString *)testID;

@end

NS_ASSUME_NONNULL_END
