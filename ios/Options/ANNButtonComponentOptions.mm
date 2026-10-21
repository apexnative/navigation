#import "ANNButtonComponentOptions.h"

@implementation ANNButtonComponentOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField doubleField:@"width"],
        [ANNOptionField doubleField:@"height"],
    ]];
}

@end
