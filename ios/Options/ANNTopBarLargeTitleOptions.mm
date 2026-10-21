#import "ANNTopBarLargeTitleOptions.h"

@implementation ANNTopBarLargeTitleOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField enumField:@"displayMode" values:@[ @"always", @"never", @"automatic", @"inline" ]],
        [ANNOptionField boolField:@"collapseOnScroll"],
        [ANNOptionField colorField:@"color"],
        [ANNOptionField fontField],
    ];
}

@end
