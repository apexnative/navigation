#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A button of the top bar or the toolbar made from the options (built by `ANNBarButtons`).
@interface ANNUIBarButtonItem : UIBarButtonItem

/// The button's `id`: `ANN.NavigationButtonPressed` names it, and a popover can point at it (`sourceButtonId`).
@property(nonatomic, copy, nullable) NSString *buttonId;

/// The id of the React component the item shows; nil for any other kind.
@property(nonatomic, copy, nullable) NSString *componentId;

@end

NS_ASSUME_NONNULL_END
