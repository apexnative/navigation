#import "ANNLayoutProtocol.h"
#import "UIViewController+LayoutProtocol.h"
#import "UINavigationController+ANNCommands.h"
#import <UIKit/UIKit.h>

/**
 * `splitView`: a column-style UISplitViewController (iOS 14+) with its columns by name: `primary`
 * (the layout's `master`), `secondary` (its `detail`), `supplementary` makes it three columns,
 * `compact` is the layout of a compact width (iPhone) and `inspector` the inspector column
 * (iPadOS 26). UIKit puts a column that is not a navigation controller (a stack) into one of its
 * own, except the compact one.
 */
@interface ANNSplitViewController : UISplitViewController <ANNLayoutProtocol, UISplitViewControllerDelegate>

/** The split view with the view controllers of its columns, by the name of the column. */
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
                           columns:(NSDictionary<NSString *, UIViewController *> *)columns;

/**
 * The view controller of the layout in a column: 'primary', 'supplementary', 'secondary',
 * 'compact' or 'inspector'.
 */
- (UIViewController *)viewControllerOfColumn:(NSString *)column;

/** Collapsed into the compact layout: UIKit shows no column then. */
@property(nonatomic, readonly) BOOL isCollapsedIntoCompact;

/**
 * `showInColumn`: the screen goes in place of what the column ('secondary' or 'supplementary') shows: the screens of
 * its stack, or the screen itself when the column is a screen alone. UIKit brings the column into view: a sidebar
 * over it goes away; collapsed into one stack the column goes on top, with back to the columns before it.
 */
- (void)showScreen:(UIViewController *)screen
              inColumn:(NSString *)column
    fromViewController:(UIViewController *)fromViewController
            completion:(ANNTransitionCompletionBlock)completion
             rejection:(ANNTransitionRejectionBlock)rejection;

/**
 * `pop` of the only screen of the secondary or supplementary column's stack while the columns show as one stack
 * (collapsed without a compact layout) and that column is on top: the column before it comes back, as the system back
 * does (the supplementary one, else the primary), and `completion` runs once that is on screen. NO, with nothing done,
 * when it doesn't apply.
 */
- (BOOL)goBackFromColumnOf:(UIViewController *)screen completion:(dispatch_block_t)completion;

@end
