#import "ANNStackPushAnimationOptions.h"

@implementation ANNStackPushAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField groupField:@"zoom" class:ANNZoomTransitionOptions.class],
    ]];
}

@end
