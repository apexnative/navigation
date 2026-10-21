#import <UIKit/UIKit.h>

// UIArrangementViewController is in the iOS 27.1 SDK: built with an older one, the app has no arrangements
// (ANNArrangementState canShowArrangements).
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100

#import "ANNArrangementScreenDelegate.h"
#import "ANNLayoutProtocol.h"
#import "UIViewController+LayoutProtocol.h"

NS_ASSUME_NONNULL_BEGIN

/// Two screens that UIKit arranges as one entry of a stack (`defineArrangement`): side by side, one above the other
/// or one over the other, by the shape of the window and the fold. The pair's bars and options are the primary's: the
/// stack gets the primary's navigation item, toolbar items, status bar, home indicator and vertical bars from it.
API_AVAILABLE(ios(27.1))
@interface ANNArrangementViewController
    : UIArrangementViewController <ANNLayoutProtocol, ANNArrangementScreenDelegate>

/// `data` is the layout's: `style`, `axes`, `fraction` and `primaryEdge`.
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
                           primary:(UIViewController *)primary
                         secondary:(UIViewController *)secondary
                              data:(NSDictionary *)data;

@property(nonatomic, strong, readonly) UIViewController *primary;
@property(nonatomic, strong, readonly) UIViewController *secondary;

@end

NS_ASSUME_NONNULL_END

#endif
