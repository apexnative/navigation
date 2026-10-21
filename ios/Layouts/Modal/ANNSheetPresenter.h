#import "ANNSheetOptions.h"
#import <UIKit/UIKit.h>

/** `modal.sheet` on the sheet a modal is shown in (a page or form sheet). */
@interface ANNSheetPresenter : NSObject

/** Before the modal is presented: its sheet takes the options; nothing for other styles. */
+ (void)applyOptions:(ANNSheetOptions *)options toModal:(UIViewController *)modal;

/**
 * `mergeOptions` of a screen in a shown modal: the options that have a value change its sheet, with
 * an animation. Nothing for a screen that is not in a sheet.
 */
+ (void)mergeOptions:(ANNSheetOptions *)options ofViewController:(UIViewController *)viewController;

/**
 * The height of the screen a modal shows, laid out at the width: the space above its React content
 * (a top bar) and the content laid out without a height.
 */
+ (CGFloat)contentHeightOf:(UIViewController *)modal width:(CGFloat)width;

/**
 * A stack in a modal shows another screen (push, pop): a sheet sized to its content (`detents: ['content']`) and a
 * modal sized to it (`contentSize.height: 'content'`) take the height of the screen now on top.
 */
+ (void)stackDidShowScreenInModal:(UINavigationController *)stack;

/** The detent of the options a sheet's detent identifier stands for: an NSString or an NSNumber. */
+ (id)detentForIdentifier:(UISheetPresentationControllerDetentIdentifier)identifier;

@end
