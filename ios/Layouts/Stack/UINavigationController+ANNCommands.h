#import "ANNLayoutProtocol.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The commands of a stack.
@interface UINavigationController (ANNCommands)

/// `showInColumn` into the split view's column `column` that is a stack: `children` in place of the stack's screens, the
/// last one on top, from the screen `fromViewController`. A navigation controller that isn't a stack of the core
/// rejects `[ANNErrorTexts columnNotStack:column:]`.
- (void)setStackChildren:(NSArray<UIViewController *> *)children
                inColumn:(NSString *)column
      fromViewController:(nullable UIViewController *)fromViewController
                animated:(BOOL)animated
              completion:(ANNTransitionCompletionBlock)completion
               rejection:(ANNTransitionRejectionBlock)rejection;

@end

NS_ASSUME_NONNULL_END
