#import "ANNModalAnimationOptions.h"

@implementation ANNModalAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField groupArrayField:@"sharedElementTransitions"
                                  class:ANNSharedElementTransitionOptions.class],
        [ANNOptionField groupArrayField:@"elementTransitions"
                                  class:ANNElementTransitionOptions.class],
        [ANNOptionField groupField:@"zoom" class:ANNZoomTransitionOptions.class],
    ]];
}

@end
