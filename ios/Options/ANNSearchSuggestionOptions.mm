#import "ANNSearchSuggestionOptions.h"

@implementation ANNSearchSuggestionOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField textField:@"title"],
        [ANNOptionField textField:@"accessibilityLabel"],
        [ANNOptionField textField:@"sfSymbol"],
        [ANNOptionField imageField:@"icon"],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
