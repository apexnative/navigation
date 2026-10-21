#import "Param.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// A number (a size, a width).
@interface Number : Param

- (nullable NSNumber *)get;
- (nullable NSNumber *)withDefault:(nullable NSNumber *)defaultValue;

@end

NS_ASSUME_NONNULL_END
