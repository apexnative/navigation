#import "ANNSetStackRootAnimationOptions.h"

@implementation ANNSetStackRootAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField boolField:@"asPush"],
    ]];
}

@end
