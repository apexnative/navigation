#import "ANNNavigationBarOptions.h"

@implementation ANNNavigationBarOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField boolField:@"drawBehind"],
    ];
}

@end
