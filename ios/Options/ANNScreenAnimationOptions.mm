#import "ANNScreenAnimationOptions.h"

@implementation ANNScreenAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField boolField:@"enabled"],
        [ANNOptionField boolField:@"waitForRender"],
    ]];
}

@end
