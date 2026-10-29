#import "ANNStackPopAnimationOptions.h"

@implementation ANNStackPopAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField boolField:@"interactive"],
    ]];
}

@end
