#import "ANNOptions.h"

/** `modal.popover`: what a popover points at and whether it stays a popover in a compact width. */
@interface ANNPopoverOptions : ANNOptions

/** A top bar or toolbar button of the presenting screen. */
@property(nonatomic, strong) Text *sourceButtonId;
/** A React view with this `nativeID` on the presenting screen. */
@property(nonatomic, strong) Text *sourceNativeID;
/** NSString 'up', 'down', 'left', 'right'; any direction when missing. */
@property(nonatomic, strong) NSArray *arrowDirections;
/** NO keeps the popover in a compact width (iPhone), where UIKit shows it as a sheet. */
@property(nonatomic, strong) Bool *adaptive;

- (BOOL)hasValue;

@end
