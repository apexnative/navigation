#import "UINavigationController+ANNCommands.h"
#import "ANNErrorTexts.h"
#import "ANNStackController.h"
#import "UIViewController+LayoutProtocol.h"

@implementation UINavigationController (ANNCommands)

// A stack of the core takes the screens through its queue, without a wait. A navigation controller of UIKit's own (the
// one UIKit puts around a column that is a screen alone) has no queue: it rejects.
- (void)setStackChildren:(NSArray<UIViewController *> *)children
                inColumn:(NSString *)column
      fromViewController:(UIViewController *)fromViewController
                animated:(BOOL)animated
              completion:(ANNTransitionCompletionBlock)completion
               rejection:(ANNTransitionRejectionBlock)rejection {
    if ([self isKindOfClass:ANNStackController.class]) {
        [(ANNStackController *)self setChildren:children
                             fromViewController:fromViewController
                                       animated:animated
                                     completion:completion
                                      rejection:rejection];
        return;
    }
    NSString *fromId = fromViewController.layoutInfo.componentId ?: @"";
    rejection([ANNErrorTexts columnNotStack:fromId column:column], nil);
}

@end
