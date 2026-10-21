#import "ANNLayoutProtocol.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The commands of a stack.
@interface UINavigationController (ANNCommands)

/// `setStackRoot`: `children` in place of the stack's screens, the last one on top, from the screen
/// `fromViewController`.
- (void)setStackChildren:(NSArray<UIViewController *> *)children
      fromViewController:(nullable UIViewController *)fromViewController
                animated:(BOOL)animated
              completion:(ANNTransitionCompletionBlock)completion
               rejection:(ANNTransitionRejectionBlock)rejection;

@end

NS_ASSUME_NONNULL_END
