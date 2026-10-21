#import "ANNComponentOptions.h"
#import "ANNComponentViewCreator.h"
#import "ANNReactView.h"

NS_ASSUME_NONNULL_BEGIN

/// The React roots of the components in one screen's options (bar title, buttons, background, search results), by the
/// id of the component. Each screen owns one, made with its creator: the same `name` and `componentId` give the same
/// root while the screen keeps it; another `name` with the id replaces it, and the old root stops. The registry holds
/// its roots until they are removed or the screen goes (`stopAll`): none is left to stop in `dealloc`. A new JavaScript
/// never finds an old root: its screens have registries of their own. Main queue.
@interface ANNReactComponentRegistry : NSObject

- (instancetype)initWithCreator:(id<ANNComponentViewCreator>)creator;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The search module's call: the React view of the component, made at the first call for its id; nil when the
/// component has no name or id, or when its content is not a React view of its own.
- (nullable ANNReactView *)createComponentIfNotExists:(ANNComponentOptions *)component
                                    parentComponentId:(NSString *)parentComponentId
                                        componentType:(ANNComponentType)componentType
                                  reactViewReadyBlock:
                                      (nullable ANNReactViewReadyCompletionBlock)reactViewReadyBlock;

/// The root of `component`, made at the first call for its id (a root with the same id and another name stops and is
/// replaced); nil when the component has no name or no id (`topBar.title.component.name`).
- (nullable UIView<ANNComponentProtocol> *)componentView:(ANNComponentOptions *)component
                                                    type:(ANNComponentType)componentType;

/// The root kept for the id; nil when none.
- (nullable UIView<ANNComponentProtocol> *)existingComponent:(NSString *)componentId;

/// Whether the root of the id rendered (its ready signal went).
- (BOOL)isComponentReady:(NSString *)componentId;

/// `handler` runs once when the root of the id is ready: at once when it is already, never when no root has the id or
/// the root goes first.
- (void)whenComponentReady:(NSString *)componentId do:(dispatch_block_t)handler;

/// The component goes: its root stops at once (once, on the main queue).
- (void)removeChildComponent:(NSString *)childId;

/// Every root still alive stops (the screen's destroy).
- (void)stopAll;

/// The roots alive now.
@property(nonatomic, readonly) NSArray<UIView<ANNComponentProtocol> *> *allComponents;

@end

NS_ASSUME_NONNULL_END
