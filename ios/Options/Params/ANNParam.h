#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// A JSON boolean: `true` or `false` as React Native gives them (a `CFBoolean`), never a number.
BOOL ANNJSONIsBoolean(id _Nullable value);

/// A JSON number that is finite: never a boolean, `NaN` or an infinity.
BOOL ANNJSONIsNumber(id _Nullable value);

/// Two values as the options keep them are the same: a boolean is never the same as a number, arrays and objects
/// compare item by item, anything else (a string, a number, `null`, a color) by `isEqual:`. nil is the same only as
/// nil.
BOOL ANNJSONValuesSame(id _Nullable a, id _Nullable b);

/// A hash of a value `ANNJSONValuesSame` compares: values that are the same have the same hash.
NSUInteger ANNJSONValueHash(id _Nullable value);

/// One value of the options as the JSON gave it. Immutable: trees of options may share it. `null` is a value too where
/// the kind has an explicit "none" (a text, a color): it takes the place of what the layers under it gave.
@interface ANNParam : NSObject

- (instancetype)initWithValue:(nullable id)value;

/// What the JSON gave, `NSNull` for `null`; nil when the key was not there or had a value of another type.
@property(nonatomic, strong, readonly, nullable) id value;

/// The options gave a value, `null` included.
- (BOOL)hasValue;

/// The options gave `null`.
- (BOOL)isNull;

/// A param of the same class with the same value: two reads of the same JSON are the same, two params without a value
/// are the same. `==` and `isEqual:` stay identity.
- (BOOL)isSameValueAs:(nullable ANNParam *)other;

/// A hash consistent with `isSameValueAs:`.
- (NSUInteger)valueHash;

@end

NS_ASSUME_NONNULL_END
