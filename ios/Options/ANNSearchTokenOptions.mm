#import "ANNSearchTokenOptions.h"

@implementation ANNSearchTokenOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField textField:@"text"],
        [ANNOptionField textField:@"sfSymbol"],
        [ANNOptionField imageField:@"icon"],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
