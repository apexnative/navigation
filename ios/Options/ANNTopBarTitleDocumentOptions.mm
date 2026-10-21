#import "ANNTopBarTitleDocumentOptions.h"

@implementation ANNTopBarTitleDocumentOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"url"],
    ];
}

@end
