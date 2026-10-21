#import "ANNTopBarTitleComponentOptions.h"

@implementation ANNTopBarTitleComponentOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField enumField:@"alignment" values:@[ @"center", @"fill" ]],
        [ANNOptionField boolField:@"waitForRender"],
    ]];
}

@end
