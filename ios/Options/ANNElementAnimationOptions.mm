#import "ANNElementAnimationOptions.h"

@implementation ANNElementAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField doubleField:@"from"],
        [ANNOptionField doubleField:@"to"],
        [ANNOptionField durationField:@"duration"],
        [ANNOptionField durationField:@"startDelay"],
        [ANNOptionField groupField:@"interpolation" class:ANNInterpolationOptions.class],
    ];
}

@end
