#import "ANNReactComponentRegistry.h"
#import "ANNAssert.h"

// One root of the registry: its name, whether it rendered, and who waits for that.
@interface ANNRegisteredComponent : NSObject
@property(nonatomic, copy) NSString *name;
@property(nonatomic, strong) UIView<ANNComponentProtocol> *view;
@property(nonatomic) BOOL ready;
@property(nonatomic, strong) NSMutableArray<dispatch_block_t> *readyHandlers;
@property(nonatomic, copy, nullable) ANNReactViewReadyCompletionBlock moduleReadyBlock;
@end

@implementation ANNRegisteredComponent
@end

@implementation ANNReactComponentRegistry {
    id<ANNComponentViewCreator> _creator;
    NSMutableDictionary<NSString *, ANNRegisteredComponent *> *_components;
}

- (instancetype)initWithCreator:(id<ANNComponentViewCreator>)creator {
    if (self = [super init]) {
        _creator = creator;
        _components = [NSMutableDictionary new];
    }
    return self;
}

- (ANNReactView *)createComponentIfNotExists:(ANNComponentOptions *)component
                           parentComponentId:(NSString *)parentComponentId
                               componentType:(ANNComponentType)componentType
                         reactViewReadyBlock:(ANNReactViewReadyCompletionBlock)reactViewReadyBlock {
    UIView<ANNComponentProtocol> *view = [self componentView:component type:componentType];
    if (!view) {
        return nil;
    }
    ANNRegisteredComponent *registered = _components[view.componentId];
    if (reactViewReadyBlock) {
        if (registered.ready) {
            reactViewReadyBlock();
        } else {
            registered.moduleReadyBlock = reactViewReadyBlock;
        }
    }
    return [view isKindOfClass:ANNReactView.class] ? (ANNReactView *)view : nil;
}

- (UIView<ANNComponentProtocol> *)componentView:(ANNComponentOptions *)component
                                           type:(ANNComponentType)componentType {
    ANNAssertMainQueue();
    NSString *name = [component.name withDefault:nil];
    NSString *componentId = [component.componentId withDefault:nil];
    if (name.length == 0 || componentId.length == 0) {
        return nil;
    }
    ANNRegisteredComponent *registered = _components[componentId];
    if (registered && [registered.name isEqualToString:name]) {
        return registered.view;
    }
    // Another name with the id: a new component in the place of the old one, which stops.
    if (registered) {
        [self removeChildComponent:componentId];
    }
    ANNRegisteredComponent *made = [ANNRegisteredComponent new];
    made.name = name;
    made.readyHandlers = [NSMutableArray new];
    __weak ANNReactComponentRegistry *weakSelf = self;
    __weak ANNRegisteredComponent *weakMade = made;
    UIView<ANNComponentProtocol> *view =
        [_creator createComponentView:name
                          componentId:componentId
                        componentType:componentType
                  reactViewReadyBlock:^{
                    [weakSelf componentBecameReady:weakMade];
                  }];
    if (!view) {
        return nil;
    }
    made.view = view;
    _components[componentId] = made;
    // A content that was ready inside its own init (none of the core's are) still counts.
    if (view.isReady) {
        [self componentBecameReady:made];
    }
    return view;
}

- (void)componentBecameReady:(ANNRegisteredComponent *)registered {
    if (!registered || registered.ready ||
        _components[registered.view.componentId ?: @""] != registered) {
        return;
    }
    registered.ready = YES;
    NSArray<dispatch_block_t> *handlers = [registered.readyHandlers copy];
    [registered.readyHandlers removeAllObjects];
    ANNReactViewReadyCompletionBlock moduleReadyBlock = registered.moduleReadyBlock;
    registered.moduleReadyBlock = nil;
    for (dispatch_block_t handler in handlers) {
        handler();
    }
    if (moduleReadyBlock) {
        moduleReadyBlock();
    }
}

- (UIView<ANNComponentProtocol> *)existingComponent:(NSString *)componentId {
    return componentId ? _components[componentId].view : nil;
}

- (BOOL)isComponentReady:(NSString *)componentId {
    return componentId && _components[componentId].ready;
}

- (void)whenComponentReady:(NSString *)componentId do:(dispatch_block_t)handler {
    ANNRegisteredComponent *registered = componentId ? _components[componentId] : nil;
    if (!registered) {
        return;
    }
    if (registered.ready) {
        handler();
        return;
    }
    [registered.readyHandlers addObject:[handler copy]];
}

- (void)removeChildComponent:(NSString *)childId {
    ANNAssertMainQueue();
    ANNRegisteredComponent *registered = childId ? _components[childId] : nil;
    if (!registered) {
        return;
    }
    [_components removeObjectForKey:childId];
    [registered.readyHandlers removeAllObjects];
    registered.moduleReadyBlock = nil;
    [registered.view stopSurface];
}

- (void)stopAll {
    ANNAssertMainQueue();
    for (NSString *componentId in _components.allKeys) {
        [self removeChildComponent:componentId];
    }
}

- (NSArray<UIView<ANNComponentProtocol> *> *)allComponents {
    NSMutableArray<UIView<ANNComponentProtocol> *> *views = [NSMutableArray new];
    for (ANNRegisteredComponent *registered in _components.allValues) {
        [views addObject:registered.view];
    }
    return views;
}

@end
