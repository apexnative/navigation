#import "ANNSplitViewColumnOptions.h"

@implementation ANNSplitViewColumnOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField numberField:@"width"],
        [ANNOptionField numberField:@"minWidth"],
        [ANNOptionField numberField:@"maxWidth"],
    ];
}

@end
