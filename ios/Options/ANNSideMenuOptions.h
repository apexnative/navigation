#import "ANNOptions.h"
#import "ANNSideMenuSideOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `sideMenu` (`OptionsSideMenu`): the menus of a side menu.
@interface ANNSideMenuOptions : ANNOptions

@property(nonatomic, strong) ANNSideMenuSideOptions *left;
@property(nonatomic, strong) ANNSideMenuSideOptions *right;
/// `'bezel'` or `'entireScreen'`.
@property(nonatomic, strong) Text *openGestureMode;
/// `'parallax'`, `'slide'`, `'slide-and-scale'` or `'door'`.
@property(nonatomic, strong) Text *animationType;
/// `'together'`, `'afterCenter'` or `'onOpen'`.
@property(nonatomic, strong) Text *menusAttachMode;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END
