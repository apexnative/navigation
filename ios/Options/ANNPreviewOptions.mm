#import "ANNPreviewOptions.h"

@implementation ANNPreviewOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"nativeID"],
        [ANNOptionField boolField:@"commit"],
        [ANNOptionField objectArrayField:@"actions"],
        [ANNOptionField doubleField:@"width"],
        [ANNOptionField doubleField:@"height"],
    ];
}

@end
