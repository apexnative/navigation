#import "ANNIconBackgroundOptions.h"

@implementation ANNIconBackgroundOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField colorField:@"color"],
        [ANNOptionField colorField:@"disabledColor"],
        [ANNOptionField doubleField:@"cornerRadius"],
        [ANNOptionField doubleField:@"width"],
        [ANNOptionField doubleField:@"height"],
    ];
}

@end
