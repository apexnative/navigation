#import "ANNComponentOptions.h"

@implementation ANNComponentOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"name"],
        [ANNOptionField textField:@"componentId"],
    ];
}

@end
