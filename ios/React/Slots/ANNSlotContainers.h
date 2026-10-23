#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A component named with this prefix is a screen of the app's one React tree: it has no React surface of its own,
/// and the slot of the tree with its componentId shows it. Events and the state give its name without the prefix.
extern NSString *const ANNSlotPrefix;

/// The name a component is known by in the events: without the slot prefix.
NSString *ANNComponentNameWithoutSlotPrefix(NSString *name);

/// A container that hears when the content of its slot mounted: the screen counts as shown once it did.
@protocol ANNSlotContainer <NSObject>
- (void)slotContentDidMount;
@end

/// A slot view of the app's React tree: it hears when the container of its slot id appears, goes or changes its size.
@protocol ANNSlotView <NSObject>
- (void)slotContainerDidChange:(NSString *)slotId;
@end

/// The native containers the slots of the app's React tree show their children in, by slot id, and the slot views by
/// the same id: a change of a container goes straight to the views of its id. A destroyed container and a deallocated
/// view drop out on their own. Main queue only.
@interface ANNSlotContainers : NSObject

+ (void)setContainer:(UIView *)container forSlotId:(NSString *)slotId;
+ (nullable UIView *)containerForSlotId:(NSString *)slotId;
/// The container goes: its slot takes its content back.
+ (void)removeContainerForSlotId:(NSString *)slotId;

/// The container of a slot appeared or changed its size: the slot views under its id, and only they, hear it.
+ (void)containerDidChange:(NSString *)slotId;

/// The slot view goes under its slot id, and out from under the id it had. Held weakly.
+ (void)registerSlotView:(id<ANNSlotView>)view forSlotId:(NSString *)slotId;
/// The slot view unmounted or lost its slot id: no change of a container reaches it.
+ (void)unregisterSlotView:(id<ANNSlotView>)view;

@end

NS_ASSUME_NONNULL_END
