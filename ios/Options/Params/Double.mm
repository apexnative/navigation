#import "Double.h"

@implementation Double

- (double)get {
    return [self.value isKindOfClass:NSNumber.class] ? [self.value doubleValue] : 0;
}

- (double)withDefault:(double)defaultValue {
    return [self.value isKindOfClass:NSNumber.class] ? [self.value doubleValue] : defaultValue;
}

@end
