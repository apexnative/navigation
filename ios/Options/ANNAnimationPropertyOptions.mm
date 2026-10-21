#import "ANNAnimationPropertyOptions.h"

@implementation ANNAnimationPropertyOptions

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
