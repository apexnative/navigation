#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `navigationBar` (`NavigationBarOptions`).
@interface ANNNavigationBarOptions : ANNOptions

@property(nonatomic, strong) Color *backgroundColor;
@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Bool *drawBehind;

@end

NS_ASSUME_NONNULL_END
