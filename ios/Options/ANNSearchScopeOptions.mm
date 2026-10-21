#import "ANNSearchScopeOptions.h"

@implementation ANNSearchScopeOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField textField:@"title"],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
