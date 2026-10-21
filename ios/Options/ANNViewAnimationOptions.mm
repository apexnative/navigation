#import "ANNViewAnimationOptions.h"

@implementation ANNViewAnimationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"x" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"y" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"translationX" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"translationY" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"alpha" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"scaleX" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"scaleY" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"rotationX" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"rotationY" class:ANNAnimationPropertyOptions.class],
        [ANNOptionField groupField:@"rotation" class:ANNAnimationPropertyOptions.class],
    ];
}

- (NSArray<ANNAnimationPropertyOptions *> *)animatedProperties {
    return @[
        _x, _y, _translationX, _translationY, _alpha, _scaleX, _scaleY, _rotationX, _rotationY,
        _rotation
    ];
}

// The set of animated properties of a side is replaced whole when the upper layer's side has any property with a value
// (the properties of two layers never add up); otherwise this layer's stay. The side's flags (`enabled`,
// `waitForRender` of the subclasses) merge by key, independently: the field table merges them below. Every merge goes
// through here (`copy`, `withDefault:`, the layers of a resolution), so a subclass needs nothing of its own.
- (void)mergeOptions:(ANNOptions *)options {
    if ([options isKindOfClass:ANNViewAnimationOptions.class] && options != self) {
        BOOL upperHasProperties = NO;
        NSArray<ANNAnimationPropertyOptions *> *upper =
            [(ANNViewAnimationOptions *)options animatedProperties];
        for (ANNAnimationPropertyOptions *property in upper) {
            if (property.hasValue) {
                upperHasProperties = YES;
                break;
            }
        }
        if (upperHasProperties) {
            _x = [ANNAnimationPropertyOptions new];
            _y = [ANNAnimationPropertyOptions new];
            _translationX = [ANNAnimationPropertyOptions new];
            _translationY = [ANNAnimationPropertyOptions new];
            _alpha = [ANNAnimationPropertyOptions new];
            _scaleX = [ANNAnimationPropertyOptions new];
            _scaleY = [ANNAnimationPropertyOptions new];
            _rotationX = [ANNAnimationPropertyOptions new];
            _rotationY = [ANNAnimationPropertyOptions new];
            _rotation = [ANNAnimationPropertyOptions new];
        }
    }
    [super mergeOptions:options];
}

@end
