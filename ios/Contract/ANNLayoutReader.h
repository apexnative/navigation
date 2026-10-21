#import "ANNLayoutNode.h"
#import "ANNLayoutRoot.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Reads the layout JSON of a command into `ANNLayoutNode`s and checks it whole, before any view is made. It runs on
/// the module's queue, where the command arrives: a failure rejects the command there with the text of the first error
/// found, and nothing goes to the main queue. It reads only the stores that are safe from any thread (the registered
/// native screens, whether the app shows pairs).
///
/// One pass over the tree, depth first, children in order, the same order on both platforms so that the first error
/// is the same text. At each node: a known `type` (`''` for a node that is no object or has no string type), an `id`
/// that is a non-empty string, the node's place under its parent (then a side only directly under a side menu), an id
/// no other node of the layout has, a screen's `data.name` and a native screen's registration, the node's rules on the
/// number of its children, each child whole, then its rules over the children's types (the sides of a side menu, the
/// columns of a split view). The id of a node is taken as it is; fields the contract doesn't have are left alone.
@interface ANNLayoutReader : NSObject

/// One node with its tree: the layout of `push`, `showModal`, `showOverlay`, `showInColumn`. Nil and the text of the
/// first error when it doesn't pass.
+ (nullable ANNLayoutNode *)nodeFrom:(nullable id)json error:(NSString *_Nullable *_Nonnull)error;

/// The layout of `setRoot`: `{ root, modals, overlays }`, one layout for the check of ids.
+ (nullable ANNLayoutRoot *)rootFrom:(nullable id)json error:(NSString *_Nullable *_Nonnull)error;

/// The layout of `setStackRoot`: the screens from the bottom up, one layout for the check of ids. Empty when JS sent
/// none: the command rejects that with the id of its screen.
+ (nullable NSArray<ANNLayoutNode *> *)nodesFrom:(nullable id)json
                                           error:(NSString *_Nullable *_Nonnull)error;

@end

NS_ASSUME_NONNULL_END
