#import "ANNLayoutProtocol.h"
#import "ANNNodeHelper.h"
#import "ANNReactView.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The readiness a wait needs of `screen`: the answer of its nearest layout parent that has one
/// (`waitedReadinessOfScreen:`), else its content.
FOUNDATION_EXPORT ANNWaitedReadiness ANNWaitedReadinessOf(UIViewController *screen);

/// Every view controller as a node of the layout: the data the core gave it (kept by its helper) and the answers of
/// `ANNLayoutProtocol` that are safe for any view controller: its view controller children are its children by the
/// layout, it shows what its current child shows, and it destroys its children. A container answers the protocol
/// methods that differ for it itself and does the common part with `nodeHelper`.
@interface UIViewController (LayoutProtocol)

/// The core's part of this node, made as it is first asked.
@property(nonatomic, strong, readonly) ANNNodeHelper *nodeHelper;

/// Whether the view controller is a node of the layout: it has the core's helper with its layout data. Asking makes
/// no helper, so it is safe on any view controller (an alert, the share sheet, a search controller).
@property(nonatomic, readonly) BOOL isLayoutNode;

#pragma mark - The node's data

@property(nonatomic, strong, nullable) ANNLayoutInfo *layoutInfo;
/// The node's own options (`ANNNodeHelper.options`): set them whole, or change them with `mergeOwnOptions:` /
/// `changeOwnOptions:`; never in place.
@property(nonatomic, strong, nullable) ANNNavigationOptions *options;
/// The default options of the app (`ANNDefaultOptions.current`); setting it does nothing.
@property(nonatomic, strong, nullable) ANNNavigationOptions *defaultOptions;
@property(nonatomic, strong, nullable) ANNEventEmitter *eventEmitter;
@property(nonatomic, strong, nullable) id<ANNComponentViewCreator> creator;
@property(nonatomic, strong, nullable) ANNNodePresenter *presenter;

#pragma mark - Places

/// The parent by the layout: the parent view controller, or the fallback a container gave.
@property(nonatomic, readonly, nullable) UIViewController *layoutParent;

/// The parent by the layout while UIKit has the view controller out of its parent.
- (void)setLayoutParentFallback:(nullable UIViewController *)layoutParent;

/// The parent by the layout whatever UIKit's parent is (`ANNNodeHelper explicitLayoutParent`): the split view of its
/// columns.
- (void)setExplicitLayoutParent:(nullable UIViewController *)layoutParent;

/// The stack the node is in, through its parents by the layout.
@property(nonatomic, readonly, nullable) UINavigationController *stack;

/// The screen this node shows now (for a screen: itself).
@property(nonatomic, readonly, nullable) UIViewController *presentedComponentViewController;

/// What the screen shows: its React view, or its slot screen view; nil for a node that is no React screen.
@property(nonatomic, readonly, nullable) UIView<ANNComponentProtocol> *reactView;

#pragma mark - Options

/// The resolved options over those of the parents and the default options: what the node shows with. Cached and
/// read-only (`ANNNodeHelper resolveOptionsWithDefault`).
- (ANNNavigationOptions *)resolveOptionsWithDefault;

/// The own options merged with `options` (`ANNNodeHelper mergeOwnOptions:`).
- (void)mergeOwnOptions:(ANNNavigationOptions *)options;

/// A change of the own options on a copy (`ANNNodeHelper changeOwnOptions:`): the way a presenter or a module keeps a
/// choice of the user (a scope, tokens, a checked menu item, a toggled button).
- (void)changeOwnOptions:(void (^)(ANNNavigationOptions *options))change;

#pragma mark - Readiness

/// A ready handler of the node under the waiter's own key (`ANNNodeHelper setReadyHandler:forKey:`): it runs once, the
/// next time the node is ready for presentation.
- (void)setReadyHandler:(dispatch_block_t)handler forKey:(NSString *)key;

/// The waiter's handler goes without running (`ANNNodeHelper removeReadyHandlerForKey:`).
- (void)removeReadyHandlerForKey:(NSString *)key;

#pragma mark - ANNLayoutProtocol, the defaults

- (nullable UIViewController *)getCurrentChild;
- (NSArray<UIViewController *> *)layoutChildren;
- (NSArray<UIViewController *> *)shownComponentViewControllers;
/// The screens a wait for the node's render waits for (`ANNLayoutProtocol`): the node's shown screens; a container
/// whose shown screens are all inside its current child answers what that child answers.
- (NSArray<UIViewController *> *)waitedComponentViewControllers;
- (void)render;
- (void)readyForPresentation;
@property(nonatomic) BOOL waitForRender;
- (ANNNavigationOptions *)resolveOptions;
- (void)mergeOptions:(ANNNavigationOptions *)options;
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child;
- (void)destroyReactView;
/// Once: the node leaves the registry and destroys its children; a second call does nothing.
- (void)destroy;
- (void)screenPopped;
- (nullable UIViewController *)systemUIChild;

@end

NS_ASSUME_NONNULL_END
