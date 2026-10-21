#import "ANNButtonOptions.h"
#import "ANNOptions.h"

// The stack's toolbar at the bottom of the screen: the screen's own buttons, shown while it is on top.
@interface ANNToolbarOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) NSArray<ANNButtonOptions *> *buttons;
@property(nonatomic, strong) Text *testID;

// Visible unless turned off, and only with buttons.
- (BOOL)shouldShow;

@end
