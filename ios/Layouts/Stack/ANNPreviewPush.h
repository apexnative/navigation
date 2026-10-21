#import "ANNCommandAnswer.h"
#import "ANNLayoutNode.h"
#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The preview of a push (`usePreview`): a push that waits for a tap on its preview. Its screen is made and renders
/// unseen, out of the stack, its key kept; the view with `preview.nativeID` is looked for on the source screen at once
/// and on the next frames, 30 tries in all; found, the preview module (`ANNPreviewInteraction`) shows the screen from
/// it on a long press. A tap that commits pushes the screen without an animation and without a wait, or goes back to
/// the entry with its key; a preview that goes without being opened destroys its screen and rejects the push. Main
/// queue.
@interface ANNPreviewPush : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The push of a preview, after the push's checks (the source in a stack, what the stack takes, the options): `source`
/// is the node of `componentId`. The answer comes with the tap, or with the preview's going.
+ (void)push:(ANNLayoutNode *)layout
        options:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById
         source:(UIViewController *)source
    componentId:(NSString *)componentId
           what:(NSString *)what
         answer:(ANNCommandAnswer *)answer;

/// `dismissPreview` of a preview that still looks for its view: it stops looking and goes (the push rejects). NO when
/// none of that id looks.
+ (BOOL)dismissSearchingPreview:(NSString *)previewId;

/// Whether `screen` is the screen of a preview that waits for its tap (shown in UIKit's menu at most).
+ (BOOL)isPreviewScreen:(UIViewController *)screen;

/// UIKit's `viewWillAppear:` of a screen: a preview screen that waits unseen and that UIKit took out of its place (the
/// menu shows it) leaves that place.
+ (void)screenWillAppear:(UIViewController *)screen;

/// A reload of JavaScript: every preview leaves its unseen place without an answer, and those still looking stop.
/// Returns the screens of those that looked: they go with the old screens (the module gives the screens of the attached
/// ones).
+ (NSArray<UIViewController *> *)takeSearchingForReload;

@end

NS_ASSUME_NONNULL_END
