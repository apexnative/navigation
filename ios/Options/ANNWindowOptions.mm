#import "ANNWindowOptions.h"

@implementation ANNWindowOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[ [ANNOptionField colorField:@"backgroundColor"] ];
}

@end
