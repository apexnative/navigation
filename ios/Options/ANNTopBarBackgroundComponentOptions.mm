#import "ANNTopBarBackgroundComponentOptions.h"

@implementation ANNTopBarBackgroundComponentOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObjectsFromArray:@[
        [ANNOptionField boolField:@"waitForRender"],
    ]];
}

@end
