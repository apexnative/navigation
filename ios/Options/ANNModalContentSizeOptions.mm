#import "ANNModalContentSizeOptions.h"

@implementation ANNModalContentSizeOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField doubleField:@"width"],
        [ANNOptionField paramField:@"height"
                             check:^BOOL(id value) {
                               return ANNJSONIsNumber(value) || [value isEqual:@"content"];
                             }],
    ];
}

@end
