#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A place where the views of nodes wait for their content unseen (their safe area views lay out only in a window): the
/// window under the current root while a root waits, a stack's view under UIKit's container while a screen of the stack
/// waits or renders, the tabs' view while a tab rendered before it shows renders its content. A parked view sits at the
/// back of its host, at the host's size and following it (a rotation, a resize), gets no touches and is hidden from
/// accessibility; it isn't a child of anything there (a tab stays the tabs' child), and its node's appearance is driven
/// meanwhile (`ANNAppearanceDispatcher driveAppearanceOf:`), so UIKit's appearance callbacks report nothing for it.
/// Each node leaves it as it was. Main queue.
@interface ANNParking : NSObject

/// `node`'s view waits in `host` from now on, with what it had kept. NO when it waits here already.
- (BOOL)park:(UIViewController *)node inView:(UIView *)host;

/// `node`'s view gets back what it had, leaves the host if it is still there (UIKit places a view it shows itself),
/// and its appearance is driven no more. `endingDeparture`: UIKit's departure of a node without a parent ends here,
/// around the removal, rather than with a `viewDidDisappear:` after the next commit, which would come after the node's
/// own appearance when it shows in the same turn (a root that becomes the window's root). Nothing when it isn't parked
/// here.
- (void)unpark:(UIViewController *)node endingDeparture:(BOOL)endingDeparture;

/// Whether `node`'s view waits here.
- (BOOL)isParked:(UIViewController *)node;

/// The nodes whose views wait here.
@property(nonatomic, readonly) NSArray<UIViewController *> *parkedNodes;

/// Whether `node`, or a layout parent of it, waits unseen in any parking.
+ (BOOL)isUnseen:(UIViewController *)node;

@end

NS_ASSUME_NONNULL_END
