#import "ANNSideMenuOptions.h"

@implementation ANNSideMenuOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"left" class:ANNSideMenuSideOptions.class],
        [ANNOptionField groupField:@"right" class:ANNSideMenuSideOptions.class],
        [ANNOptionField enumField:@"openGestureMode" values:@[ @"bezel", @"entireScreen" ]],
        [ANNOptionField enumField:@"animationType"
                           values:@[ @"parallax", @"slide", @"slide-and-scale", @"door" ]],
        [ANNOptionField enumField:@"menusAttachMode"
                           values:@[ @"together", @"afterCenter", @"onOpen" ]],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
