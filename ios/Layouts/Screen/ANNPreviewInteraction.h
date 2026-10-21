#import <UIKit/UIKit.h>

/**
 * `preview` of a push (`usePreview`): a long press on the view shows the pushed screen as a
 * preview, with its `preview.actions` as a menu; a tap on the preview commits it (UIKit's context
 * menu, on every device and with a pointer on iPad).
 */
@interface ANNPreviewInteraction : NSObject <UIContextMenuInteractionDelegate>

/**
 * Previews `preview` from `view`, the component view with the tag `reactTag`, and replaces an earlier
 * preview of the view. `commit` runs on a tap on the preview and returns whether it showed the
 * screen; the preview goes then, as the push it stands for is done. `dismissed` runs once when the
 * preview goes without being shown as a screen: replaced by another preview of the view, after
 * Fabric gave the view to another component, or when `source`, the screen of the view, goes.
 */
+ (void)attachPreview:(UIViewController *)preview
               toView:(UIView *)view
             reactTag:(NSInteger)reactTag
               source:(UIViewController *)source
               commit:(BOOL (^)(void))commit
            dismissed:(void (^)(void))dismissed;

/**
 * Takes away the preview of the screen with the id `componentId`, as it goes any other way without
 * being shown as a screen: `dismissed` runs. Returns NO when no preview of that screen is attached
 * (a tap showed the screen, the preview went already, or it still looks for its view).
 */
+ (BOOL)dismissPreviewOfScreen:(NSString *)componentId;

/**
 * After a reload: every preview goes, without `dismissed` (the new JavaScript never asked for it).
 * Returns their screens, which still have running React views: the caller destroys them once React
 * Native started the surfaces again.
 */
+ (NSArray<UIViewController *> *)removeAllPreviewsAfterReload;

@end
