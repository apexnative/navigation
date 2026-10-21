#import "ANNStackAnimationOptions.h"

@implementation ANNStackAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"enabled"],
        [ANNOptionField boolField:@"waitForRender"],
        [ANNOptionField groupField:@"content" class:ANNEnterExitScreenAnimationOptions.class],
        [ANNOptionField groupField:@"topBar" class:ANNEnterExitTopBarAnimationOptions.class],
        [ANNOptionField groupField:@"bottomTabs"
                             class:ANNEnterExitBottomTabsAnimationOptions.class],
        [ANNOptionField groupArrayField:@"sharedElementTransitions"
                                  class:ANNSharedElementTransitionOptions.class],
        [ANNOptionField groupArrayField:@"elementTransitions"
                                  class:ANNElementTransitionOptions.class],
    ];
}

@end
