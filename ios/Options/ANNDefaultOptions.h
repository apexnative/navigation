#import "ANNNavigationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// The default options of the app (`setDefaultOptions`): one tree for the process, replaced as a whole by each
/// `setDefaultOptions`. Every node reads it when it resolves its options, so new defaults reach every layer, shown or
/// waiting, and nodes made later. Main queue.
@interface ANNDefaultOptions : NSObject

/// The defaults now; an empty tree before the first `setDefaultOptions`. Read-only: never change it in place.
@property(class, nonatomic, readonly) ANNNavigationOptions *current;

/// Replaces the defaults (never merges) and makes every resolved options miss its cache.
+ (void)setCurrent:(ANNNavigationOptions *)options;

@end

NS_ASSUME_NONNULL_END
