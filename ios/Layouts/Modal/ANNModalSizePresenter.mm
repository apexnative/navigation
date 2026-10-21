#import "ANNModalSizePresenter.h"
#import "ANNReactView.h"
#import "ANNSheetPresenter.h"
#import "UIViewController+LayoutProtocol.h"

static NSString *const ANNContentSize = @"contentSize";

@implementation ANNModalSizePresenter

+ (void)applyOptions:(ANNModalOptions *)options toModal:(UIViewController *)modal {
    ANNModalContentSizeOptions *size = options.contentSize;
    if (!size.hasValue) {
        return;
    }
    [self setSize:size ofModal:modal];
    // Fabric reports no size changes: the content is measured after it mounts.
    UIView<ANNComponentProtocol> *reactView = modal.presentedComponentViewController.reactView;
    if (![[size.height withDefault:nil] isEqual:@"content"]) {
        [reactView setContentDidMount:nil forKey:ANNContentSize];
        return;
    }
    __weak UIViewController *weakModal = modal;
    [reactView
        setContentDidMount:^{
          [self setSize:size ofModal:weakModal];
        }
                    forKey:ANNContentSize];
}

+ (void)mergeOptions:(ANNModalOptions *)options
    ofViewController:(UIViewController *)viewController {
    if (!options.contentSize.hasValue) {
        return;
    }
    UIViewController *modal = viewController;
    while (modal.parentViewController) {
        modal = modal.parentViewController;
    }
    if (modal.presentingViewController) {
        [self applyOptions:modal.resolveOptionsWithDefault.modal toModal:modal];
    }
}

// A width of 0 is the width UIKit gives the style; the content is measured at the modal's width.
+ (void)setSize:(ANNModalContentSizeOptions *)size ofModal:(UIViewController *)modal {
    if (!modal) {
        return;
    }
    CGFloat width = [size.width withDefault:0];
    id height = [size.height withDefault:@0];
    CGFloat points = 0;
    if ([height isEqual:@"content"]) {
        points =
            [ANNSheetPresenter contentHeightOf:modal
                                         width:width > 0 ? width : modal.view.bounds.size.width];
    } else if ([height isKindOfClass:NSNumber.class]) {
        points = [height doubleValue];
    }
    CGSize preferred = CGSizeMake(width, points);
    if (!CGSizeEqualToSize(modal.preferredContentSize, preferred)) {
        modal.preferredContentSize = preferred;
    }
}

@end
