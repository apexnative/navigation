#import "ANNLayoutProtocol.h"
#import "UIViewController+LayoutProtocol.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Which part of a side menu a child is: `SideMenuCenter`, `SideMenuLeft`, `SideMenuRight` in the layout.
typedef NS_ENUM(NSInteger, ANNSideMenuChildType) {
    ANNSideMenuChildTypeCenter,
    ANNSideMenuChildTypeLeft,
    ANNSideMenuChildTypeRight,
};

/// A part of a side menu: the wrapper of the center or of a menu, with the layout it holds. The sides are places, not
/// layouts: nothing finds a side by its id, so the wrapper is never in the registry. Its child is its UIKit child from
/// the start and fills its view; the side menu drives the wrapper's appearance, which UIKit forwards to the child. Its
/// system UI and its merges follow its child: the home indicator, the deferred edges and the vertical bars are the
/// child's, a merge inside goes to the side menu.
@interface ANNSideMenuChildViewController : UIViewController <ANNLayoutProtocol>

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
                             child:(UIViewController *)child
                              type:(ANNSideMenuChildType)type;

@property(nonatomic, readonly) ANNSideMenuChildType type;

/// The layout of the part.
@property(nonatomic, strong, readonly) UIViewController *child;

/// The id of the part's view for e2e tests: `<sideMenu.testID>.center`, a menu's own `sideMenu.<side>.testID` or
/// `<sideMenu.testID>.<side>`; nil: no id. Kept until the view loads.
@property(nonatomic, copy, nullable) NSString *testID;

@end

NS_ASSUME_NONNULL_END
