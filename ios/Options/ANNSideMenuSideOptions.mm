#import "ANNSideMenuSideOptions.h"

@implementation ANNSideMenuSideOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField boolField:@"animate"],
        [ANNOptionField boolField:@"enabled"],
        [ANNOptionField doubleField:@"width"],
        [ANNOptionField doubleField:@"height"],
        [ANNOptionField boolField:@"shouldStretchDrawer"],
        [ANNOptionField doubleField:@"animationVelocity"],
        [ANNOptionField enumField:@"openMode" values:@[ @"aboveContent", @"pushContent" ]],
        [ANNOptionField boolField:@"glass"],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
