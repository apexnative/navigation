#import "ANNLayoutNode.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The checks of a layout that need what shows now, made by a command on the main queue before it makes any view. Each
/// command calls those it needs. Main queue.
@interface ANNLayoutValidator : NSObject

/// The first id of the tree, depth first, that names a node the app has already (shown, or made and waiting to
/// show) and that `allowed` doesn't excuse; nil when there is none. The sides of side menus don't count: they are
/// places, not nodes. The command rejects with `[ANNErrorTexts alreadyShown:]`: `push`, `showModal`, `setRoot` (against
/// the modals and overlays that stay), `setStackRoot` (the ids of its own stack allowed).
+ (nullable NSString *)firstShownIdIn:(ANNLayoutNode *)tree
                              allowed:(nullable BOOL (^)(ANNLayoutNode *node))allowed;

/// The text when the stack `stackId` can't take the node a `push` or `setStackRoot` brings: anything but a screen or
/// a pair. Nil when it can.
+ (nullable NSString *)checkStackChild:(ANNLayoutNode *)node stackId:(NSString *)stackId;

@end

NS_ASSUME_NONNULL_END
