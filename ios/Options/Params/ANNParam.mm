#import "ANNParam.h"

BOOL ANNJSONIsBoolean(id value) {
    return [value isKindOfClass:NSNumber.class] &&
           CFGetTypeID((__bridge CFTypeRef)value) == CFBooleanGetTypeID();
}

BOOL ANNJSONIsNumber(id value) {
    return [value isKindOfClass:NSNumber.class] && !ANNJSONIsBoolean(value) && isfinite([value doubleValue]);
}

BOOL ANNJSONValuesSame(id a, id b) {
    if (a == b) {
        return YES;
    }
    if (!a || !b) {
        return NO;
    }
    if ([a isKindOfClass:NSArray.class]) {
        if (![b isKindOfClass:NSArray.class] || [a count] != [b count]) {
            return NO;
        }
        NSArray *items = b;
        __block BOOL same = YES;
        [(NSArray *)a enumerateObjectsUsingBlock:^(id item, NSUInteger index, BOOL *stop) {
          same = ANNJSONValuesSame(item, items[index]);
          *stop = !same;
        }];
        return same;
    }
    if ([a isKindOfClass:NSDictionary.class]) {
        if (![b isKindOfClass:NSDictionary.class] || [a count] != [b count]) {
            return NO;
        }
        NSDictionary *fields = b;
        __block BOOL same = YES;
        [(NSDictionary *)a enumerateKeysAndObjectsUsingBlock:^(id key, id value, BOOL *stop) {
          same = ANNJSONValuesSame(value, fields[key]);
          *stop = !same;
        }];
        return same;
    }
    // `isEqual:` of NSNumber takes `true` for 1: a badge of `true` (a dot) and of `1` (a number) are not the same.
    if (ANNJSONIsBoolean(a) != ANNJSONIsBoolean(b)) {
        return NO;
    }
    return [a isEqual:b];
}

// An object's hash is its count only (Foundation's); an array mixes its items in, so lists of other values differ.
NSUInteger ANNJSONValueHash(id value) {
    if ([value isKindOfClass:NSArray.class]) {
        NSUInteger hash = [value count];
        for (id item in value) {
            hash = hash * 31 + ANNJSONValueHash(item);
        }
        return hash;
    }
    return [value hash];
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

- (BOOL)isSameValueAs:(ANNParam *)other {
    if (other == self) {
        return YES;
    }
    return other.class == self.class && ANNJSONValuesSame(_value, other.value);
}

- (NSUInteger)valueHash {
    return ANNJSONValueHash(_value);
}

@end
