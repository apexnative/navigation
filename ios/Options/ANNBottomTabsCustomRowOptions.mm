#import "ANNBottomTabsCustomRowOptions.h"

@implementation ANNBottomTabsCustomRowOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField doubleField:@"height"],
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField enumField:@"backgroundEffect" values:@[ @"glass", @"blur", @"none" ]],
        [ANNOptionField doubleField:@"cornerRadius"],
        [ANNOptionField doubleField:@"horizontalMargin"],
        [ANNOptionField doubleField:@"bottomMargin"],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
