#import "ANNBottomTabsAnimationOptions.h"

@implementation ANNBottomTabsAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField boolField:@"enabled"],
    ]];
}

@end
