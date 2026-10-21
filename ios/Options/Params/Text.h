#import "Param.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// A text value.
@interface Text : Param

/// A value set by the native side, as a user choice the options keep.
+ (Text *)withValue:(nullable NSString *)value;

- (nullable NSString *)get;
- (nullable NSString *)withDefault:(nullable NSString *)defaultValue;

@end

NS_ASSUME_NONNULL_END
