#import "ANNStatusBarOptions.h"

@implementation ANNStatusBarOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField enumField:@"style" values:@[ @"light", @"dark" ]],
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField boolField:@"drawBehind"],
        [ANNOptionField boolField:@"translucent"],
        [ANNOptionField boolField:@"animate"],
        [ANNOptionField boolField:@"hideWithTopBar"],
        [ANNOptionField boolField:@"blur"],
    ];
}

@end
