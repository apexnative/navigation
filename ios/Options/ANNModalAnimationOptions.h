#import "ANNElementTransitionOptions.h"
#import "ANNEnterExitScreenAnimationOptions.h"
#import "ANNSharedElementTransitionOptions.h"
#import "ANNZoomTransitionOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `animations.showModal`, `animations.dismissModal` (`ModalAnimationOptions`).
@interface ANNModalAnimationOptions : ANNEnterExitScreenAnimationOptions

@property(nonatomic, strong, nullable) NSArray<ANNSharedElementTransitionOptions *> *sharedElementTransitions;
@property(nonatomic, strong, nullable) NSArray<ANNElementTransitionOptions *> *elementTransitions;
@property(nonatomic, strong) ANNZoomTransitionOptions *zoom;

@end

NS_ASSUME_NONNULL_END
