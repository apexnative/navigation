#import <ApexNavigation/ApexNavigation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A screen built entirely with UIKit (no React) that @apexnative/navigation can
/// push, present as a modal or put into a tab. Its context has the props from JS, and runs the
/// functions of the props and the commands of the navigation in JS.
@interface NativeProfileViewController : UIViewController
- (instancetype)initWithContext:(ANNNativeScreenContext *)context;
@end

NS_ASSUME_NONNULL_END
