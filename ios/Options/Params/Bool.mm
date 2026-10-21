#import "Bool.h"

@implementation Bool

+ (Bool *)withValue:(BOOL)value {
    return [[Bool alloc] initWithValue:@(value)];
}

- (BOOL)get {
    return [self.value isKindOfClass:NSNumber.class] ? [self.value boolValue] : 0;
}

- (BOOL)withDefault:(BOOL)defaultValue {
    return [self.value isKindOfClass:NSNumber.class] ? [self.value boolValue] : defaultValue;
}

@end
