#import "ANNTopBarScrollEdgeAppearanceBackgroundOptions.h"

@implementation ANNTopBarScrollEdgeAppearanceBackgroundOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField colorField:@"color"],
        [ANNOptionField boolField:@"translucent"],
    ];
}

@end
