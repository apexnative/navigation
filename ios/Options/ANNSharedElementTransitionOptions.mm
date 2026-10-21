#import "ANNSharedElementTransitionOptions.h"

@implementation ANNSharedElementTransitionOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"fromId"],
        [ANNOptionField textField:@"toId"],
        [ANNOptionField durationField:@"duration"],
        [ANNOptionField durationField:@"startDelay"],
        [ANNOptionField groupField:@"interpolation" class:ANNInterpolationOptions.class],
    ];
}

@end
