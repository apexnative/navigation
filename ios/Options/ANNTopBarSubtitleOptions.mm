#import "ANNTopBarSubtitleOptions.h"

@implementation ANNTopBarSubtitleOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"text"],
        [ANNOptionField fontField],
        [ANNOptionField colorField:@"color"],
        [ANNOptionField enumField:@"alignment" values:@[ @"center" ]],
    ];
}

@end
