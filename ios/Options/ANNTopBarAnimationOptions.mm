#import "ANNTopBarAnimationOptions.h"

@implementation ANNTopBarAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField boolField:@"enabled"],
    ]];
}

@end
