#import "ANNParam.h"

NS_ASSUME_NONNULL_BEGIN

/// A whole number, or the value of an enumeration the options name by a string.
@interface ANNIntegerParam : ANNParam

/// The value; 0 without one.
- (NSInteger)get;
- (NSInteger)withDefault:(NSInteger)defaultValue;

@end

NS_ASSUME_NONNULL_END
