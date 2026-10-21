#import "ANNFabOptions.h"

@implementation ANNFabOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField colorField:@"clickColor"],
        [ANNOptionField colorField:@"rippleColor"],
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField imageField:@"icon"],
        [ANNOptionField colorField:@"iconColor"],
        [ANNOptionField enumField:@"alignHorizontally" values:@[ @"left", @"right" ]],
        [ANNOptionField boolField:@"hideOnScroll"],
        [ANNOptionField enumField:@"size" values:@[ @"mini", @"regular" ]],
        [ANNOptionField groupArrayField:@"actions" class:ANNFabOptions.class],
        [ANNOptionField colorField:@"scrimColor"],
    ];
}

@end
