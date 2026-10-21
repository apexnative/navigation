#import "ANNBottomTabsShadowOptions.h"

@implementation ANNBottomTabsShadowOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField doubleField:@"opacity"],
        [ANNOptionField colorField:@"color"],
        [ANNOptionField doubleField:@"radius"],
    ];
}

@end
