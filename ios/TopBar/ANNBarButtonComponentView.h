#import "ANNReactView.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The slot of a React button (`topBar.leftButtons[].component`) as the custom view of its bar item. It takes a content
/// view (`UIView<ANNComponentProtocol>`), never a class of the roots. In the glass design of iOS 26 (`ANNTabsDesign`)
/// the slot is 44 pt high and as wide as the content, at least 44 pt; the width follows the content both ways
/// (measured after every mount; a change over 0.5 pt only), and the slot's constraints live with the view, so a new
/// item built around it reuses them. In the compatibility mode and before iOS 26: the content's size, no
/// constraints. A button rendered before the screen shows enters at its width. The core measures only the surface and
/// never moves the views inside it: the JS wrapper centres them.
@interface ANNBarButtonComponentView : UIView

- (instancetype)initWithContent:(UIView<ANNComponentProtocol> *)content;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

@property(nonatomic, strong, readonly) UIView<ANNComponentProtocol> *content;

/// The width the slot measured; 0 before the content rendered.
@property(nonatomic, readonly) CGFloat measuredWidth;

/// Measures the content again.
- (void)measure;

@end

NS_ASSUME_NONNULL_END
