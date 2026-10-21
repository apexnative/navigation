#import "ANNTopBarScrollEdgeAppearanceTitleOptions.h"

@implementation ANNTopBarScrollEdgeAppearanceTitleOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField fontField],
        [ANNOptionField colorField:@"color"],
    ];
}

@end
