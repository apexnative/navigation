#import "ANNComponentViewController.h"
#import "ApexNavigation.h"

NS_ASSUME_NONNULL_BEGIN

/// A native screen of the layout (`ExternalComponent`): the view controller an app's creator made
/// (`registerNativeScreen:creator:`), with no React content of its own. The layout factory makes the screen's context
/// and calls the creator at the layout's creation, then gives both to the screen. The app's view controller is a child
/// view controller whose view fills the screen and follows its size; the screen's `navigationItem` is the child's, so
/// the bar's options go there. The screen is ready at once, has no insets of its own (it keeps its edges itself),
/// reports its appearance through a record of its own, and sends `ANN.NativeScreenReleased` once when it is destroyed.
@interface ANNExternalViewController : ANNComponentViewController

/// The context of the screen (id, props, functions, the emitter of its JavaScript), kept with the screen:
/// `updateNativeScreenProps` applies to it.
@property(nonatomic, strong, readonly, nullable) ANNNativeScreenContext *nativeScreenContext;

/// The view controller of the native screen; nil before the screen got it.
@property(nonatomic, strong, readonly, nullable) UIViewController *nativeViewController;

/// The context and the app's view controller made for this screen, once: the view controller becomes the child of the
/// screen, its view goes in as the screen's view loads.
- (void)embedNativeViewController:(UIViewController *)nativeViewController
                          context:(ANNNativeScreenContext *)context;

@end

NS_ASSUME_NONNULL_END
