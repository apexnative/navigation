#import "ANNButtonsParser.h"

@implementation ANNButtonsParser

+ (NSArray<ANNButtonOptions *> *)parse:(id)buttons {
    if (![buttons isKindOfClass:NSArray.class]) {
        return nil;
    }
    NSMutableArray<ANNButtonOptions *> *parsed = [NSMutableArray new];
    for (id button in buttons) {
        if ([button isKindOfClass:NSDictionary.class]) {
            [parsed addObject:[[ANNButtonOptions alloc] initWithDict:button]];
        }
    }
    return [parsed copy];
}

@end
