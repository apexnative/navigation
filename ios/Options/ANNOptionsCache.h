#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The one point where options change: a number for the whole process that grows each time a node's own options, its
/// user layer or the default options change. A node's resolved options are cached with the number they were built at,
/// so any change makes every cache miss. Main queue.
///
/// Exactly three places call `optionsChanged`: the setter of a node's own options (`ANNNodeHelper.options`, which
/// `mergeOwnOptions:` and `changeOwnOptions:` go through), the setter of its user layer
/// (`ANNNodeHelper setUserChoice:forKey:`) and the store of new default options (`ANNDefaultOptions`). Nothing else may
/// change own options, choices or defaults: a write past them would leave caches stale.
@interface ANNOptionsCache : NSObject

/// The generation of the options now.
@property(class, nonatomic, readonly) NSUInteger generation;

/// Own options of a node or the default options changed.
+ (void)optionsChanged;

@end

NS_ASSUME_NONNULL_END
