#import "ANNLayoutNode.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The layout of `setRoot`, read and checked: the new root, then the modals to show over it in order, then the
/// overlays. Immutable.
@interface ANNLayoutRoot : NSObject

- (instancetype)initWithRoot:(ANNLayoutNode *)root
                      modals:(NSArray<ANNLayoutNode *> *)modals
                    overlays:(NSArray<ANNLayoutNode *> *)overlays NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, strong, readonly) ANNLayoutNode *root;
@property(nonatomic, copy, readonly) NSArray<ANNLayoutNode *> *modals;
@property(nonatomic, copy, readonly) NSArray<ANNLayoutNode *> *overlays;

/// The root, the modals and the overlays, in that order.
- (NSArray<ANNLayoutNode *> *)trees;

@end

NS_ASSUME_NONNULL_END
