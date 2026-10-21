#import "ANNCommandAnswer.h"
#import "ANNLayoutNode.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The split view's commands after the runner's shared steps and the screen found (`showInColumn`, `setColumnShown`).
/// The split view of a screen is the nearest of its layout parents that has columns (`viewControllerOfColumn:`): the
/// core never names the module's class. Main queue.
@interface ANNSplitViewCommands : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `showInColumn`: the push's checks of the layout (the options of the tree, what the core makes), then the screen is
/// made and waits by the push's rules. Once it is ready, by the state then: the source in a split view not collapsed
/// into its compact layout — the split view shows the screen in `column`, unless a later request of that column began
/// meanwhile (the last one wins); else the source in a stack — a push over it; else a rejection. The answer is the
/// shown screen's id; every rejection destroys the screen.
+ (void)showInColumn:(NSString *)column
              layout:(ANNLayoutNode *)layout
         componentId:(NSString *)componentId
                what:(NSString *)what
              answer:(ANNCommandAnswer *)answer;

/// `setColumnShown`: `primary`, `supplementary` or `inspector` (anything else is `primary`) shows or hides with UIKit's
/// transition; the answer (`null`) once it ended, at once without one or without the column.
+ (void)setColumnShown:(NSString *)column
                 shown:(BOOL)shown
           componentId:(NSString *)componentId
                answer:(ANNCommandAnswer *)answer;

/// A reload of JavaScript: the screens that wait to show in a column stop waiting and leave their place in the window
/// without an answer; the requests are forgotten. Returns the screens: they go with the old ones.
+ (NSArray<UIViewController *> *)takeAllForReload;

@end

NS_ASSUME_NONNULL_END
