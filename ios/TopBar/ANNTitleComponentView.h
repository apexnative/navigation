#import "ANNReactView.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The container of a React title (`topBar.title.component`) in the item's `titleView`. It takes a content view
/// (`UIView<ANNComponentProtocol>`), never a class of the roots, so another source of content can plug in later.
/// `center` (and unset): the size of the content, measured against unbounded space after every mount (Fabric reports no
/// change of size); a size with a zero side or the one the view has is ignored, another calls `sizeDidChange`, which
/// sets the view into the item again. `fill`: the frame the presenter gives (the row of the bar), the content laid out
/// by the view's bounds. The core never measures or moves the views inside the content: only the surface's size is
/// asked.
@interface ANNTitleComponentView : UIView

- (instancetype)initWithContent:(UIView<ANNComponentProtocol> *)content;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

@property(nonatomic, strong, readonly) UIView<ANNComponentProtocol> *content;

/// `fill`: the view takes the frame the presenter sets; otherwise the content's size.
@property(nonatomic) BOOL fills;

/// `fill`: the row the presenter placed the view in, the size it asks UIKit for; UIKit may narrow its frame between
/// the side buttons. Zero until placed, and again when the view stops filling.
@property(nonatomic) CGSize rowSize;

/// Called after a mount changed the content's size (`center`).
@property(nonatomic, copy, nullable) dispatch_block_t sizeDidChange;

/// Measures the content again (after a mount or when the place changed).
- (void)measure;

@end

NS_ASSUME_NONNULL_END
