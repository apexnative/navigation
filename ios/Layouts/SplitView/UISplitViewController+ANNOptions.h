#import "Number.h"
#import "ANNSplitViewOptions.h"
#import <UIKit/UIKit.h>

@interface UISplitViewController (ANNOptions)

- (void)ann_setDisplayMode:(NSString *)displayMode;

- (void)ann_setPrimaryEdge:(NSString *)primaryEdge;

- (void)ann_setMinWidth:(Number *)minWidth;

- (void)ann_setMaxWidth:(Number *)maxWidth;

- (void)ann_setPrimaryBackgroundStyle:(NSString *)style;

/**
 * The preferred width of the primary column, the split behavior, the display mode button and the
 * gesture, with the values the options have.
 */
- (void)ann_setBehavior:(ANNSplitViewOptions *)options;

/** The widths of the supplementary column that have a value. */
- (void)ann_setSupplementaryColumn:(ANNSplitViewColumnOptions *)options;

/** iPadOS 26: the widths of the inspector column that have a value. */
- (void)ann_setInspectorColumn:(ANNSplitViewColumnOptions *)options;

/** iPadOS 26: shows or hides the inspector column; nothing without a value. */
- (void)ann_setInspectorVisible:(Bool *)visible animated:(BOOL)animated;

@end
