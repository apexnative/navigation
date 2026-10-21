#import "ANNHardwareBackButtonOptions.h"

@implementation ANNHardwareBackButtonOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField enumField:@"bottomTabsOnPress" values:@[ @"exit", @"first", @"previous" ]],
        [ANNOptionField boolField:@"popStackOnPress"],
        [ANNOptionField boolField:@"dismissModalOnPress"],
    ];
}

@end
