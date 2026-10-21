#import "ANNScrollEdgeOptions.h"

@implementation ANNScrollEdgeOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"hidden"],
        [ANNOptionField enumField:@"style" values:@[ @"automatic", @"soft", @"hard" ]],
    ];
}

@end
