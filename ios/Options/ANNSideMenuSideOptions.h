#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `sideMenu.left`, `sideMenu.right` (`SideMenuSide`). `visible` is a one-shot option: a node's own options never keep
/// it.
@interface ANNSideMenuSideOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Bool *animate;
@property(nonatomic, strong) Bool *enabled;
@property(nonatomic, strong) Double *width;
@property(nonatomic, strong) Double *height;
@property(nonatomic, strong) Bool *shouldStretchDrawer;
@property(nonatomic, strong) Double *animationVelocity;
/// `'aboveContent'` or `'pushContent'`.
@property(nonatomic, strong) Text *openMode;
@property(nonatomic, strong) Bool *glass;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END
