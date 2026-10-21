#import "ANNScrollEdgeEffectOptions.h"

@implementation ANNScrollEdgeEffectOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"hidden"],
        [ANNOptionField enumField:@"style" values:@[ @"automatic", @"soft", @"hard" ]],
        [ANNOptionField groupField:@"top" class:ANNScrollEdgeOptions.class],
        [ANNOptionField groupField:@"bottom" class:ANNScrollEdgeOptions.class],
        [ANNOptionField groupField:@"left" class:ANNScrollEdgeOptions.class],
        [ANNOptionField groupField:@"right" class:ANNScrollEdgeOptions.class],
    ];
}

@end
