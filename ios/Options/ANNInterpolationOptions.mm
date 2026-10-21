#import "ANNInterpolationOptions.h"

@implementation ANNInterpolationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField enumField:@"type"
                           values:@[
                               @"linear", @"accelerate", @"decelerate", @"accelerateDecelerate",
                               @"decelerateAccelerate", @"fastOutSlowIn", @"overshoot", @"spring"
                           ]],
        [ANNOptionField doubleField:@"factor"],
        [ANNOptionField doubleField:@"tension"],
        [ANNOptionField doubleField:@"mass"],
        [ANNOptionField doubleField:@"damping"],
        [ANNOptionField doubleField:@"stiffness"],
        [ANNOptionField boolField:@"allowsOverdamping"],
        [ANNOptionField doubleField:@"initialVelocity"],
    ];
}

@end
