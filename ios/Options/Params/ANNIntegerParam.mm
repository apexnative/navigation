#import "ANNIntegerParam.h"

@implementation ANNIntegerParam

- (NSInteger)get {
    return [self.value isKindOfClass:NSNumber.class] ? [self.value integerValue] : 0;
}

- (NSInteger)withDefault:(NSInteger)defaultValue {
    return [self.value isKindOfClass:NSNumber.class] ? [self.value integerValue] : defaultValue;
}

@end
