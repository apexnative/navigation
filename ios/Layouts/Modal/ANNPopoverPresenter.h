#import "ANNPopoverOptions.h"
#import <UIKit/UIKit.h>

/** `modal.popover`: the source a popover points at. */
@interface ANNPopoverPresenter : NSObject

/**
 * The popover points at the button or the view of the options on the presenting screen. Without
 * one it stands in the middle of the presenting screen without an arrow: UIKit throws when a
 * popover has no anchor in a regular width.
 */
+ (void)anchorPopover:(UIPopoverPresentationController *)popover
              options:(ANNPopoverOptions *)options
           presenting:(UIViewController *)presenting;

/**
 * `adaptive: false`: the popover stays a popover in a compact width. Its presentation controller
 * gets a delegate that keeps the style and passes everything else to the delegate.
 */
+ (void)keepPopoverOf:(UIViewController *)modal
             delegate:(id<UIAdaptivePresentationControllerDelegate>)delegate;

/** A top bar or toolbar button with the id on the screen the layout shows. */
+ (UIBarButtonItem *)barButtonWithId:(NSString *)buttonId in:(UIViewController *)layout;

/** The React view with the `nativeID` in the hierarchy of the view. */
+ (UIView *)viewWithNativeID:(NSString *)nativeID in:(UIView *)root;

@end
