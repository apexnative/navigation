#import "ANNAnimationOptions.h"

@implementation ANNAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"push" class:ANNStackPushAnimationOptions.class],
        [ANNOptionField groupField:@"pop" class:ANNStackPopAnimationOptions.class],
        [ANNOptionField groupField:@"setStackRoot" class:ANNSetStackRootAnimationOptions.class],
        [ANNOptionField groupField:@"setRoot" class:ANNEnterExitScreenAnimationOptions.class],
        [ANNOptionField groupField:@"showModal" class:ANNModalAnimationOptions.class],
        [ANNOptionField groupField:@"dismissModal" class:ANNModalAnimationOptions.class],
    ];
}

@end
