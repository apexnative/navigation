#import "ANNModalOptions.h"
#import <UIKit/UIKit.h>

/** `modal.contentSize`: the size of a form sheet or a popover. */
@interface ANNModalSizePresenter : NSObject

/** Before the modal is presented, and once it is shown (the content is measured at its width). */
+ (void)applyOptions:(ANNModalOptions *)options toModal:(UIViewController *)modal;

/** `mergeOptions` of a screen in a shown modal: the modal takes the size of the options. */
+ (void)mergeOptions:(ANNModalOptions *)options ofViewController:(UIViewController *)viewController;

@end
