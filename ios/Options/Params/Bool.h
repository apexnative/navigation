#import "ANNParam.h"

NS_ASSUME_NONNULL_BEGIN

/// A true or false value.
@interface Bool : ANNParam

/// A value set by the native side, as a user choice the options keep.
+ (Bool *)withValue:(BOOL)value;

/// The value; 0 without one.
- (BOOL)get;
- (BOOL)withDefault:(BOOL)defaultValue;

@end

NS_ASSUME_NONNULL_END
