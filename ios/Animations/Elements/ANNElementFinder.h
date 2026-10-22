#import "ANNFrameDriver.h"
#import <UIKit/UIKit.h>

@class Text;

NS_ASSUME_NONNULL_BEGIN

/// Finds the React views of the elements of a transition by their `nativeID`: depth first, the first match, in the
/// React view of the screen a side of the transition shows — the visible screen of the side's node
/// (`+[ANNAppearanceDispatcher visibleScreenOf:]`: a modal's stack its top, tabs the selected one; a pair its primary).
/// Every transition of the core finds its elements here; nothing is changed. Main queue.
@interface ANNElementFinder : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The React view of the visible screen of `node`; nil without one (a native screen, no screen).
+ (nullable UIView *)searchRootOf:(nullable UIViewController *)node;

/// The view with `nativeID` in `root` and its subviews; nil when none has it or `nativeID` is empty.
+ (nullable UIView *)viewWithNativeID:(nullable NSString *)nativeID inView:(nullable UIView *)root;

/// The view with `nativeID` in the visible screen of `node`.
+ (nullable UIView *)viewWithNativeID:(nullable NSString *)nativeID
                               inNode:(nullable UIViewController *)node;

@end

/// The `nativeID` an element's option holds; nil when it holds none (not a string, an empty one).
FOUNDATION_EXPORT NSString *_Nullable ANNNativeIDOf(Text *_Nullable value);

/// The isolation of every element of the core's transitions: `work`, a step of the element named `element` (its
/// `nativeID`, or a pair's two), runs; an exception in it skips that element only — one line in the log
/// (`ANNElementSkipped`), and NO — while the rest of the transition plays and its command completes.
FOUNDATION_EXPORT BOOL ANNElementStep(NSString *element, NS_NOESCAPE dispatch_block_t work);

/// The line in the log for the element named `element`, skipped for `reason`: it shows and hides with its screen.
FOUNDATION_EXPORT void ANNElementSkipped(NSString *element, NSString *reason);

/// An element's frames, each in `ANNElementStep`: after a frame that fails, the element's values go back
/// (`-restore`), and it gets no frame more.
@interface ANNIsolatedElement : NSObject <ANNFrameClient>

- (instancetype)initWithClient:(id<ANNFrameClient>)client
                          name:(NSString *)name NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
