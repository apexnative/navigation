#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Posted when the container of a slot appears or changes its size; the object is the slot id.
extern NSNotificationName const ANNSlotContainerDidChangeNotification;

/// A component named with this prefix is a screen of the app's one React tree: it has no React surface of its own,
/// and the slot of the tree with its componentId shows it. Events and the state give its name without the prefix.
extern NSString *const ANNSlotPrefix;

/// The name a component is known by in the events: without the slot prefix.
NSString *ANNComponentNameWithoutSlotPrefix(NSString *name);

/// A container that hears when the content of its slot mounted: the screen counts as shown once it did.
@protocol ANNSlotContainer <NSObject>
- (void)slotContentDidMount;
@end

/// The native containers the slots of the app's React tree show their children in, by slot id. A destroyed
/// container drops out on its own.
@interface ANNSlotContainers : NSObject

+ (void)setContainer:(UIView *)container forSlotId:(NSString *)slotId;
+ (nullable UIView *)containerForSlotId:(NSString *)slotId;
/// The container goes: its slot takes its content back.
+ (void)removeContainerForSlotId:(NSString *)slotId;

/// The container of a slot appeared or changed its size.
+ (void)containerDidChange:(NSString *)slotId;

@end

NS_ASSUME_NONNULL_END
