#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// What identifies a node of the layout: its id (`componentId`, the one JS gave it) and, for a screen, its name.
@interface ANNLayoutInfo : NSObject

- (instancetype)initWithComponentId:(NSString *)componentId name:(nullable NSString *)name;

@property(nonatomic, copy, readonly) NSString *componentId;
@property(nonatomic, copy, readonly, nullable) NSString *name;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
