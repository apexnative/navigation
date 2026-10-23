#import "ANNUserChoice.h"

@implementation ANNUserChoice {
    ANNUserChoiceApply _apply;
    ANNUserChoiceMerged _merged;
}

+ (instancetype)choiceWithValue:(id)value
                          apply:(ANNUserChoiceApply)apply
                         merged:(ANNUserChoiceMerged)merged {
    return [[self alloc] initWithValue:value apply:apply merged:merged];
}

- (instancetype)initWithValue:(id)value
                        apply:(ANNUserChoiceApply)apply
                       merged:(ANNUserChoiceMerged)merged {
    if (self = [super init]) {
        _value = value;
        _apply = [apply copy];
        _merged = [merged copy];
    }
    return self;
}

- (instancetype)choiceWithValue:(id)value {
    return [[ANNUserChoice alloc] initWithValue:value apply:_apply merged:_merged];
}

- (void)applyTo:(ANNNavigationOptions *)options {
    _apply(options, _value);
}

- (ANNUserChoice *)choiceAfterMerge:(ANNNavigationOptions *)merge
                                own:(ANNNavigationOptions *)own
                             before:(ANNNavigationOptions *)before
                              after:(ANNNavigationOptions *)after {
    id value = _merged(_value, merge, own, before, after);
    if (!value) {
        return nil;
    }
    return value == _value ? self : [self choiceWithValue:value];
}

@end
