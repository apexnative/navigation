#import "ANNTopBarBackgroundOptions.h"

@implementation ANNTopBarBackgroundOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField colorField:@"color"],
        [ANNOptionField boolField:@"clipToBounds"],
        [ANNOptionField groupField:@"component" class:ANNTopBarBackgroundComponentOptions.class],
        [ANNOptionField boolField:@"translucent"],
        [ANNOptionField boolField:@"blur"],
    ];
}

@end
