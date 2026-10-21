#import "ANNModalOptions.h"

@implementation ANNModalOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"sheet" class:ANNSheetOptions.class],
        [ANNOptionField groupField:@"contentSize" class:ANNModalContentSizeOptions.class],
        [ANNOptionField groupField:@"popover" class:ANNPopoverOptions.class],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField boolField:@"swipeToDismiss"],
    ];
}

@end
