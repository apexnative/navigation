#import "ANNEnterExitBottomTabsAnimationOptions.h"

@implementation ANNEnterExitBottomTabsAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"enter" class:ANNBottomTabsAnimationOptions.class],
        [ANNOptionField groupField:@"exit" class:ANNBottomTabsAnimationOptions.class],
    ];
}

@end
