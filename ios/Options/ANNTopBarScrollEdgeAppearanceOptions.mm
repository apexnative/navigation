#import "ANNTopBarScrollEdgeAppearanceOptions.h"

@implementation ANNTopBarScrollEdgeAppearanceOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"title" class:ANNTopBarScrollEdgeAppearanceTitleOptions.class],
        [ANNOptionField groupField:@"background"
                             class:ANNTopBarScrollEdgeAppearanceBackgroundOptions.class],
        [ANNOptionField boolField:@"active"],
        [ANNOptionField boolField:@"noBorder"],
        [ANNOptionField colorField:@"borderColor"],
    ];
}

@end
