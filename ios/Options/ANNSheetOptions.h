#import "ANNOptions.h"

/**
 * `modal.sheet`: the heights a page or form sheet stops at and how it looks. A detent is
 * 'medium', 'large', a height in points or 'content' (the height of the screen's content); heights
 * and 'content' need iOS 16.
 */
@interface ANNSheetOptions : ANNOptions

/**
 * The detents as the options give them, in any order and with repeats: NSString ('medium', 'large', 'content') or
 * NSNumber. ANNSheetPresenter sorts them and drops the repeats.
 */
@property(nonatomic, strong) NSArray *detents;
@property(nonatomic, strong) Param *selectedDetent;
/** The largest detent the screen below stays undimmed and touchable at. */
@property(nonatomic, strong) Param *largestUndimmedDetent;
@property(nonatomic, strong) Bool *grabber;
/**
 * Scrolling up at the top of the content moves the sheet to a larger detent (UIKit default YES).
 */
@property(nonatomic, strong) Bool *expandsOnScroll;
@property(nonatomic, strong) Double *cornerRadius;
@property(nonatomic, strong) Text *testID;

- (BOOL)hasValue;

@end
