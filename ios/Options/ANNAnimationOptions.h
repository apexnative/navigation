#import "ANNEnterExitScreenAnimationOptions.h"
#import "ANNModalAnimationOptions.h"
#import "ANNOptions.h"
#import "ANNSetStackRootAnimationOptions.h"
#import "ANNStackAnimationOptions.h"
#import "ANNStackPopAnimationOptions.h"
#import "ANNStackPushAnimationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `animations` (`AnimationOptions`): the animations of the commands.
@interface ANNAnimationOptions : ANNOptions

@property(nonatomic, strong) ANNStackPushAnimationOptions *push;
@property(nonatomic, strong) ANNStackPopAnimationOptions *pop;
@property(nonatomic, strong) ANNSetStackRootAnimationOptions *setStackRoot;
@property(nonatomic, strong) ANNEnterExitScreenAnimationOptions *setRoot;
@property(nonatomic, strong) ANNModalAnimationOptions *showModal;
@property(nonatomic, strong) ANNModalAnimationOptions *dismissModal;

@end

NS_ASSUME_NONNULL_END
