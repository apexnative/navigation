#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// A JSON boolean: `true` or `false` as React Native gives them (a `CFBoolean`), never a number.
BOOL ANNJSONIsBoolean(id _Nullable value);

/// A JSON number that is finite: never a boolean, `NaN` or an infinity.
BOOL ANNJSONIsNumber(id _Nullable value);

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

@end

NS_ASSUME_NONNULL_END
