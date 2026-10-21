#import "ANNEnterExitTopBarAnimationOptions.h"

@implementation ANNEnterExitTopBarAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"enter" class:ANNTopBarAnimationOptions.class],
        [ANNOptionField groupField:@"exit" class:ANNTopBarAnimationOptions.class],
    ];
}

@end
