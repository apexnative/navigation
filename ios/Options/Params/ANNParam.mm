#import "ANNParam.h"

BOOL ANNJSONIsBoolean(id value) {
    return [value isKindOfClass:NSNumber.class] &&
           CFGetTypeID((__bridge CFTypeRef)value) == CFBooleanGetTypeID();
}

BOOL ANNJSONIsNumber(id value) {
    return [value isKindOfClass:NSNumber.class] && !ANNJSONIsBoolean(value) && isfinite([value doubleValue]);
}

@implementation ANNParam

- (instancetype)initWithValue:(id)value {
    if (self = [super init]) {
        _value = value;
    }
    return self;
}

- (BOOL)hasValue {
    return _value != nil;
}

- (BOOL)isNull {
    return _value == NSNull.null;
}

@end
