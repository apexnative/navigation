#import "ANNIconInsetsOptions.h"

@implementation ANNIconInsetsOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField doubleField:@"top"],
        [ANNOptionField doubleField:@"left"],
        [ANNOptionField doubleField:@"bottom"],
        [ANNOptionField doubleField:@"right"],
    ];
}

@end
