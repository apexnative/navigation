#import "ANNLayoutProtocol.h"
#import "ANNSideMenuChildViewController.h"
#import "ANNSideMenuTypes.h"
#import "UIViewController+LayoutProtocol.h"
#import <UIKit/UIKit.h>

/**
 * `sideMenu`: the center and a left and a right menu. A menu opens with a pan (from the edge or
 * anywhere on the center, `openGestureMode`) or from the options, over the center or pushing it
 * aside (`openMode`); a tap or a pan on the center closes it.
 */
@interface ANNSideMenuViewController : UIViewController <ANNLayoutProtocol>

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
              childViewControllers:(NSArray *)childViewControllers
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter;

@property(nonatomic, strong, readonly) ANNSideMenuChildViewController *center;
@property(nonatomic, strong, readonly) ANNSideMenuChildViewController *left;
@property(nonatomic, strong, readonly) ANNSideMenuChildViewController *right;
/** The open menu: from the end of its opening to the end of its closing; none while closed and while opening. */
@property(nonatomic, readonly) ANNSideMenuSide openSide;

- (void)side:(ANNSideMenuSide)side enabled:(BOOL)enabled;
- (void)side:(ANNSideMenuSide)side visible:(BOOL)visible;
/** Opens or closes the menu; without animation at once, with the same appearance calls in the same order. */
- (void)side:(ANNSideMenuSide)side visible:(BOOL)visible animated:(BOOL)animated;
- (void)side:(ANNSideMenuSide)side width:(double)width;
- (void)side:(ANNSideMenuSide)side openMode:(ANNSideMenuOpenMode)openMode;
/** A pan past the width stretches the menu a little. */
- (void)side:(ANNSideMenuSide)side shouldStretch:(BOOL)shouldStretch;
/** Points per second of the open and close animations. */
- (void)side:(ANNSideMenuSide)side animationVelocity:(double)velocity;
/** iOS 26: the menu on Liquid Glass, which shows where the menu's screen has no background. */
- (void)side:(ANNSideMenuSide)side glass:(BOOL)glass;
- (void)setOpenGestureMode:(ANNSideMenuOpenGestureMode)openGestureMode;
/** How a menu under the center appears: 'door', 'parallax', 'slide' or 'slide-and-scale'. */
- (void)setAnimationType:(NSString *)animationType;
/** The id of the shield over the center while the menu of this side shows (e2e tests); nil: no id. */
- (void)side:(ANNSideMenuSide)side scrimTestID:(NSString *)testID;

@end
