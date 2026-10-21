#import "ANNEnterExitScreenAnimationOptions.h"

@implementation ANNEnterExitScreenAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"enter" class:ANNScreenAnimationOptions.class],
        [ANNOptionField groupField:@"exit" class:ANNScreenAnimationOptions.class],
    ];
}

@end
