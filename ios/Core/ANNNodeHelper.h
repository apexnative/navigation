#import "ANNComponentViewCreator.h"
#import "ANNEventEmitter.h"
#import "ANNLayoutInfo.h"
#import "ANNNavigationOptions.h"
#import "ANNNodePresenter.h"
#import "ANNUserChoice.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The key of a container's ready handler on each of its children (a pair waits for both of its screens).
FOUNDATION_EXPORT NSString *const ANNContainerReadyHandlerKey;

/// The core's part of a layout node: the data the node got at creation and the work every node shares. The core makes
/// one for each view controller as it is first asked (`UIViewController+LayoutProtocol`), and the view controller keeps
/// it. A container calls it for the common part of its answers.
@interface ANNNodeHelper : NSObject

- (instancetype)initWithNode:(UIViewController *)node;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The view controller of the node.
@property(nonatomic, weak, readonly, nullable) UIViewController *node;

@property(nonatomic, strong, nullable) ANNLayoutInfo *layoutInfo;
/// The node's own options: its layout options with every `mergeOptions` into it and what the core keeps of a merge (the
/// title component a merged text drops, side colors). The user's choices are not here but in the user layer
/// (`setUserChoice:forKey:`). The setter stores them without the one-shot options and makes every resolved options miss
/// its cache; it is the only way to change them. Never change them in place: `mergeOwnOptions:` or
/// `changeOwnOptions:`.
@property(nonatomic, strong, nullable) ANNNavigationOptions *options;
/// The default options of the app (`ANNDefaultOptions.current`); setting it does nothing, the defaults are the app's.
@property(nonatomic, strong, nullable) ANNNavigationOptions *defaultOptions;
@property(nonatomic, strong, nullable) ANNEventEmitter *eventEmitter;
@property(nonatomic, strong, nullable) id<ANNComponentViewCreator> creator;
@property(nonatomic, strong, nullable) ANNNodePresenter *presenter;
@property(nonatomic) BOOL waitForRender;

/// The parent by the layout when UIKit took the node out of its parent (a screen of a pair out of the window, a tab of
/// More) or put it into one that is no node (UIKit's More navigation).
@property(nonatomic, weak, nullable) UIViewController *layoutParentFallback;

/// The parent by the layout whatever UIKit's parent is: a column of a split view, which UIKit moves into the primary
/// column's stack while the columns show as one stack, or wraps in a navigation controller of its own. It wins over
/// UIKit's parent and the fallback while it is set.
@property(nonatomic, weak, nullable) UIViewController *explicitLayoutParent;

/// The common setup of a node: keeps its data, binds the presenter to the node and applies the initial options. A nil
/// `eventEmitter` keeps the emitter of the JavaScript that runs now (`ApexNavigation.eventEmitter`).
- (void)setUpWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                    creator:(nullable id<ANNComponentViewCreator>)creator
                    options:(nullable ANNNavigationOptions *)options
             defaultOptions:(nullable ANNNavigationOptions *)defaultOptions
                  presenter:(nullable ANNNodePresenter *)presenter
               eventEmitter:(nullable ANNEventEmitter *)eventEmitter;

/// The node's parent by the layout: the explicit one when set; else its parent view controller when that is a node of
/// the layout, else the fallback (when there is none, the parent view controller).
- (nullable UIViewController *)layoutParent;

/// The app's `mergeOptions` into the node: `options` merged with `options` given and stored through the setter. A
/// choice of the user layer that the merge changes leaves the layer, by Android's rules
/// (`ANNUserChoice choiceAfterMerge:own:before:after:`): a field given a value other than the app had there (the own
/// options', else the node's resolution before the merge), or the button, item or scope of the choice gone from the
/// node's resolution after it; the same value sent again keeps it.
- (void)mergeOwnOptions:(ANNNavigationOptions *)options;

/// A change of the own options by the core: `change` gets a copy of them, and the copy is stored through the setter.
/// For what the core keeps of the app's merges (the title component a merged text drops, side colors), never for a
/// choice of the user, which goes into the user layer.
- (void)changeOwnOptions:(void (^)(ANNNavigationOptions *options))change;

/// The user layer: the choices the user made in native UI (a toggled button, the check of a single-selection menu, the
/// search scope, the tokens and suggestions UIKit removed, a renamed title), one per key, apart from the own options.
/// The resolution writes them over the own options, by identity (`ANNUserChoice applyTo:`): defaults < parents < own <
/// user. Setting one (nil removes it) makes every resolved options miss its cache, as a change of own options does.
/// `setDefaultOptions` never touches the layer; it goes with the node.
- (void)setUserChoice:(nullable ANNUserChoice *)choice forKey:(NSString *)key;

/// The choice under `key`, or nil.
- (nullable ANNUserChoice *)userChoiceForKey:(NSString *)key;

/// The common resolve: a copy of the node's own options with its user layer written over them, then the resolved
/// options of its current child over that, down the visible chain. A new object each call: a container's override may
/// change it in place.
- (ANNNavigationOptions *)resolveOptions;

/// What the node shows with: the default options, then the own options of its parents (each with its user layer) from
/// the farthest to the nearest, then the node's `resolveOptions`; a node without a current child (a screen) writes its
/// user layer once more at the end, so a choice also reaches a list the screen takes from its stack or the defaults.
/// Cached until any options or user layer change (`ANNOptionsCache`), the node has another parent, or a node of its
/// visible chain has another current child. Read-only: copy it before changing anything.
- (ANNNavigationOptions *)resolveOptionsWithDefault;

/// `resolveOptionsWithDefault` built now, without the cache: for a reader whose node resolves through more than its
/// visible chain (the side menu reads its center while a menu is open), which the cache's key doesn't follow. A new
/// object each call.
- (ANNNavigationOptions *)resolveOptionsWithDefaultUncached;

/// The common render: the current child renders. A node that waits for its children to say "ready" (a pair) does it
/// itself.
- (void)render;

/// Sends "ready": every ready handler set now runs, once, in the order they were first set, and is dropped.
- (void)readyForPresentation;

/// `handler` runs the next time the node is ready for presentation (`readyForPresentation`), once. One handler per
/// key: each waiter (a command's render wait, the stack's lower screens, a container on its children) sets and removes
/// only its own, so that waiters never take each other's place. Setting a key again replaces its handler. Destroying the
/// node (`markDestroyed`) drops every handler.
- (void)setReadyHandler:(dispatch_block_t)handler forKey:(NSString *)key;

/// The handler of `key` goes without running; nothing when there is none.
- (void)removeReadyHandlerForKey:(NSString *)key;

/// The node is known by its id from now on (`ANNNodeRegistry`): the layout factory calls it for every node it makes,
/// right after making it, but for the sides of side menus.
- (void)registerNode;

/// Whether the node was destroyed.
@property(nonatomic, readonly) BOOL destroyed;

/// The node is being destroyed: YES the first time, NO after, so that a destroy runs once. Its ready handlers go.
- (BOOL)markDestroyed;

/// The node is known by its id no more; a later node with the same id stays. The default `destroy` calls it; a node
/// that destroys itself otherwise calls it too.
- (void)unregisterNode;

@end

NS_ASSUME_NONNULL_END
