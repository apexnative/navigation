#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Every node of the app by its id: the nodes of the main window and of the overlay windows, of every modal, the nodes
/// made and still waiting to show, and the screens of previews. A node registers when the layout is made (the sides of
/// side menus never: they are places) and leaves when it is destroyed; the registry is empty after a reload of
/// JavaScript. Commands and modules find a node here, never by walking the views.
///
/// The nodes are held weakly: a node that went away without leaving is not found. Main queue.
@interface ANNNodeRegistry : NSObject

/// `node` is the one named `componentId` from now on.
+ (void)registerNode:(UIViewController *)node componentId:(NSString *)componentId;

/// `node` leaves; a later node registered under the same id keeps it.
+ (void)unregisterNode:(UIViewController *)node componentId:(NSString *)componentId;

/// The node named `componentId`, or nil.
+ (nullable UIViewController *)nodeForComponentId:(NSString *)componentId;

/// Every node there is.
+ (NSArray<UIViewController *> *)allNodes;

/// Forgets every node: the JavaScript that named them went.
+ (void)clear;

@end

NS_ASSUME_NONNULL_END
