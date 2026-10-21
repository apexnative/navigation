#import "ANNBottomTabDotIndicatorOptions.h"

@implementation ANNBottomTabDotIndicatorOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField colorField:@"color"],
        [ANNOptionField doubleField:@"size"],
        [ANNOptionField boolField:@"visible"],
    ];
}

@end
