#import "ANNLayoutNode.h"
#import "ANNNavigationOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// The options of a command's layout, read on the main queue before any view is made. Every command with a layout calls
/// it first: node by node, depth first in the order of the layout, so the first unreadable value is the same on iOS and
/// Android.
@interface ANNLayoutTreeOptions : NSObject

/// The options of every node of `tree` by its id (the ids of one command are unique). Nil when a value can't be read:
/// `rejection` gets `unreadableOption(path, nodeId)` of the first one, which the command rejects with.
+ (nullable NSDictionary<NSString *, ANNNavigationOptions *> *)optionsOfTree:(ANNLayoutNode *)tree
                                                                    rejection:(NSString *_Nullable *_Nonnull)rejection;

/// The same for several trees in order (`setRoot`: the root, the modals, the overlays; `setStackRoot`: its screens).
+ (nullable NSDictionary<NSString *, ANNNavigationOptions *> *)optionsOfTrees:(NSArray<ANNLayoutNode *> *)trees
                                                                     rejection:(NSString *_Nullable *_Nonnull)rejection;

@end

NS_ASSUME_NONNULL_END
