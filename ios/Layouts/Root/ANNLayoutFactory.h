#import "ANNLayoutNode.h"
#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Makes the view controllers of a checked layout. Each node gets its id and name, its options read by
/// `ANNLayoutTreeOptions`, the default options, a presenter, the content creator and the emitter of the JavaScript that
/// runs now (`ApexNavigation.eventEmitter`), and is registered by its id right after it is made (`ANNNodeRegistry`;
/// never a side of a side menu). Nothing renders here: a node makes its content in `render`. Main queue.
///
/// Each layout type has a maker: `Component`, `ExternalComponent`, `Stack`, `BottomTabs`, `SideMenuRoot` with its
/// sides, `SplitView`, and `Arrangement` where UIKit has pairs (iOS 27.1 and its SDK).
@interface ANNLayoutFactory : NSObject

/// Whether every node of the trees has a maker: a command checks it before it makes anything.
+ (BOOL)canMakeTrees:(NSArray<ANNLayoutNode *> *)trees;

/// The view controller of `node` with its whole subtree. `optionsById` has the options of every node by its id; a node
/// missing from it gets empty options. An exception of a maker destroys what was made of the tree, and goes on to the
/// command, which rejects.
+ (UIViewController *)nodeFromLayout:(ANNLayoutNode *)node
                             options:
                                 (NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
