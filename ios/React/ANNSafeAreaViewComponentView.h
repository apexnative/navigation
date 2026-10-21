#import <React/RCTViewComponentView.h>

NS_ASSUME_NONNULL_BEGIN

/// The safe area view (`ANNSafeAreaView`): keeps its content out of its screen's safe area, by a padding in the layout.
@interface ANNSafeAreaViewComponentView : RCTViewComponentView

/// The screen is rendered into the view, also when it renders nothing (`null`).
@property(nonatomic, readonly) BOOL contentRendered;

@end

NS_ASSUME_NONNULL_END
