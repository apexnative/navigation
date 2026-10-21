#import "ANNOptions.h"
#import "ANNTopBarAnimationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `EnterExitAnimationOptions` of the top bar.
@interface ANNEnterExitTopBarAnimationOptions : ANNOptions

@property(nonatomic, strong) ANNTopBarAnimationOptions *enter;
@property(nonatomic, strong) ANNTopBarAnimationOptions *exit;

@end

NS_ASSUME_NONNULL_END
