#import "ANNElementTransitionOptions.h"

@implementation ANNElementTransitionOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField groupField:@"alpha" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"x" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"y" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"translationX" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"translationY" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"scaleX" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"scaleY" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"rotationX" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"rotationY" class:ANNElementAnimationOptions.class],
        [ANNOptionField groupField:@"rotation" class:ANNElementAnimationOptions.class],
    ];
}

- (NSArray<ANNElementAnimationOptions *> *)animatedProperties {
    return @[
        _x, _y, _translationX, _translationY, _alpha, _scaleX, _scaleY, _rotationX, _rotationY,
        _rotation
    ];
}

@end
