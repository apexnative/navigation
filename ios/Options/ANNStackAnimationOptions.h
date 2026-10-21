#import "ANNElementTransitionOptions.h"
#import "ANNEnterExitBottomTabsAnimationOptions.h"
#import "ANNEnterExitScreenAnimationOptions.h"
#import "ANNEnterExitTopBarAnimationOptions.h"
#import "ANNOptions.h"
#import "ANNSharedElementTransitionOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `StackAnimationOptions`: the animation of a change of a stack.
@interface ANNStackAnimationOptions : ANNOptions

@property(nonatomic, strong) Bool *enabled;
@property(nonatomic, strong) Bool *waitForRender;
@property(nonatomic, strong) ANNEnterExitScreenAnimationOptions *content;
@property(nonatomic, strong) ANNEnterExitTopBarAnimationOptions *topBar;
@property(nonatomic, strong) ANNEnterExitBottomTabsAnimationOptions *bottomTabs;
@property(nonatomic, strong, nullable) NSArray<ANNSharedElementTransitionOptions *> *sharedElementTransitions;
@property(nonatomic, strong, nullable) NSArray<ANNElementTransitionOptions *> *elementTransitions;

@end

NS_ASSUME_NONNULL_END
