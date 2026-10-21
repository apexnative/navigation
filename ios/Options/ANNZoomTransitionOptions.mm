#import "ANNZoomTransitionOptions.h"

@implementation ANNZoomTransitionOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"fromId"],
        [ANNOptionField boolField:@"enabled"],
    ];
}

@end
