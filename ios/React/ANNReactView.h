#import "ANNComponentType.h"
#import "ANNEventEmitter.h"
#import <React/RCTFabricSurface.h>
#import <UIKit/UIKit.h>

@class ANNScreenAppearance;
@class RCTHost;

NS_ASSUME_NONNULL_BEGIN

/// Called once, on the main queue, when a React view is ready: its content rendered (or mounted). No content waits on
/// a timer of its own: a waiting command has its limit.
typedef void (^ANNReactViewReadyCompletionBlock)(void);

/// What a screen shows, whoever makes it: the core's React view (a React surface of its own) or the slot screen view of
/// one React tree (`ANNSlotScreenView`). The core talks to a screen's content only through this.
@protocol ANNComponentProtocol <NSObject>

/// The id of the screen or component.
@property(nonatomic, readonly) NSString *componentId;

- (ANNReactComponentType)componentType;

/// The appearance of the content as JavaScript is told it: the content owns it, made with the content's id, its name
/// without the `slot:` prefix, its type and the content itself as what it waits for.
@property(nonatomic, strong, readonly) ANNScreenAppearance *appearance;

/// The lifecycle of the screen as it shows and goes: `ANN.ComponentWillAppear`, `DidAppear`, `DidDisappear`. Each
/// hands the content's `appearance` to `ANNAppearanceDispatcher`, which holds the events until the content mounted (at
/// most 0.5 s), keeps them balanced and sends them.
- (void)componentWillAppear;
- (void)componentDidAppear;
- (void)componentDidDisappear;

/// The ready signal went, for good: the content rendered (a React root) or mounted (a slot screen).
@property(nonatomic, readonly) BOOL isReady;

/// Called each time content mounts in the view, one handler for each key; nil removes the handler of the key.
- (void)setContentDidMount:(nullable void (^)(void))handler forKey:(NSString *)key;

/// The screen goes: its React content stops.
- (void)stopSurface;

/// Touches that no React view takes go through to what is under the view.
@property(nonatomic, assign) BOOL passThroughTouches;

/// The React surface the content is measured with (its size); nil when the content has no surface of its own.
@property(nonatomic, readonly, nullable) RCTFabricSurface *surface;

@optional

/// The content's root mounted once ("root mounted"): its first mount, whatever it rendered. A content whose readiness
/// is its mount (the slot screen view) has no separate answer: its `isReady` says it.
@property(nonatomic, readonly) BOOL hasMounted;

/// `handler` runs once, at the first mount of the content's root; at once when it mounted already. Dropped when the
/// content stops.
- (void)whenMounted:(dispatch_block_t)handler;

/// The height of the content laid out at the width of its container without a height limit, for a content without a
/// surface of its own (a slot screen): 0 while nothing is measured yet. The first question starts the measure; each
/// later mount of the content has the height of its last layout.
- (CGFloat)contentHeightAtWidth:(CGFloat)width;

@end

/// The core's React view of a screen or a component: a React surface of its own, made at init with the props
/// `{ componentId }` and started by the host once its JavaScript runs, sized to the view exactly, in a hosting view
/// that keeps the app's theme to the window's (`ANNSurfaceHostingView`). It is ready, once and for good, when the safe
/// area view of its id says its content rendered (`ANNScreenReadiness`); a root without a safe area view is ready at a
/// mount of its surface once its root view has children. It hears every mount of its own surface (the surface
/// presenter's observer) and runs its content-mount handlers there while ready. A component of a bar or a tab cell (any
/// type but `ANNComponentTypeComponent` and the tabs' accessory) renders at once: the view marks its id so.
/// `stopSurface` stops the surface once, on the main queue; nothing stops it in `dealloc`.
@interface ANNReactView : UIView <ANNComponentProtocol>

/// `componentId` must not be empty: a view without it is never made (an exception, which rejects the command that
/// asked for it). Main queue.
- (instancetype)initWithHost:(nullable RCTHost *)host
                 componentId:(NSString *)componentId
                        name:(NSString *)name
               componentType:(ANNComponentType)componentType
                eventEmitter:(nullable ANNEventEmitter *)eventEmitter
         reactViewReadyBlock:(nullable ANNReactViewReadyCompletionBlock)reactViewReadyBlock;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

/// The name of the component in JavaScript.
@property(nonatomic, copy, readonly) NSString *moduleName;

/// The props the native side gives the root (a tab cell's `tabIndex`, `selected`, `badge`, `dot`, `dotColor`), as an
/// update of the same root's props: the surface gets `{ componentId }` with these over it, and JavaScript keeps the
/// `passProps` it holds by the id. Nothing when they are the ones it has.
- (void)setNativeProps:(NSDictionary<NSString *, id> *)nativeProps;

/// The surface takes the hosting view's size again when the size React Native lays it out at isn't the hosting view's
/// (the hosting view sizes it only in its own layout). Called at every layout of this view and by the screen at every
/// recompute of its root frame; nothing when they agree.
- (void)updateSurfaceSizeIfNeeded;

@end

NS_ASSUME_NONNULL_END
