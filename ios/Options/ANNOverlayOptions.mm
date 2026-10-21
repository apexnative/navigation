#import "ANNOverlayOptions.h"

@implementation ANNOverlayOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"interceptTouchOutside"],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
