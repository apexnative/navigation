#import "ANNCommandAnswer.h"
#import "ANNLayoutNode.h"
#import <UIKit/UIKit.h>

@class ANNStackController;

NS_ASSUME_NONNULL_BEGIN

/// The stack's commands after the runner's shared steps and the screen found. Every check comes before any node is
/// made, in this order: the screen, its stack, the ids shown already, what a stack takes, the options of the trees,
/// what the core makes. Every check reads the stack as JavaScript sees it (`ANNStackQueue.entries`). `what` completes
/// the text of a failure later in the queue. Main queue.
@interface ANNStackCommands : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The stack a command from `node` acts in: the node when it is a stack, else its nearest stack among its layout
/// parents (a navigation controller of UIKit's own isn't one). Nil when it is in none.
+ (nullable ANNStackController *)stackOf:(UIViewController *)node;

/// `push`; a node with `options.preview.nativeID` is the preview of a push (`ANNPreviewPush`).
+ (void)push:(ANNLayoutNode *)layout
    componentId:(NSString *)componentId
           what:(NSString *)what
         answer:(ANNCommandAnswer *)answer;

/// `showInColumn`'s push over its source: `node`, made from `layout` and waited for already, over the screen
/// `componentId` in its stack, with the push's checks of that stack (what a stack takes, the entries' keys), its
/// animation (`animations.push.enabled`) and its answer, and no second wait. Every rejection destroys `node`; a key
/// that leads back to an entry destroys it too.
+ (void)pushMadeNode:(UIViewController *)node
              layout:(ANNLayoutNode *)layout
         componentId:(NSString *)componentId
                what:(NSString *)what
              answer:(ANNCommandAnswer *)answer;

+ (void)pop:(NSString *)componentId what:(NSString *)what answer:(ANNCommandAnswer *)answer;

+ (void)popTo:(NSString *)componentId what:(NSString *)what answer:(ANNCommandAnswer *)answer;

+ (void)popToRoot:(NSString *)componentId what:(NSString *)what answer:(ANNCommandAnswer *)answer;

+ (void)setStackRoot:(NSArray<ANNLayoutNode *> *)layouts
         componentId:(NSString *)componentId
                what:(NSString *)what
              answer:(ANNCommandAnswer *)answer;

@end

NS_ASSUME_NONNULL_END
