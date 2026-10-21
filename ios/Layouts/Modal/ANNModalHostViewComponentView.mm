#import "ANNModalHostViewComponentView.h"
#import <React/RCTComponentViewFactory.h>
#import <React/UIView+React.h>

@implementation ANNModalHostViewComponentView

+ (void)registerForModalHostView {
    // The class has the descriptor of ModalHostView and replaces React Native's for it.
    [[RCTComponentViewFactory currentComponentViewFactory] registerComponentViewClass:self];
}

- (void)presentViewController:(UIViewController *)modalViewController
                     animated:(BOOL)animated
                   completion:(void (^)(void))completion {
    UIViewController *presenter = [self reactViewController];
    // The presented view controller of a screen is also the one its parents present.
    while (presenter.presentedViewController &&
           !presenter.presentedViewController.isBeingDismissed) {
        presenter = presenter.presentedViewController;
    }
    [presenter presentViewController:modalViewController animated:animated completion:completion];
}

@end
