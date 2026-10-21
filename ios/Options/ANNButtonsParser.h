#import "ANNButtonOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads a list of buttons (`*Buttons`, `toolbar.buttons`) for a group that reads its own JSON (the toolbar).
@interface ANNButtonsParser : NSObject

/// A button for each object of the list; nil when `buttons` is not a list.
+ (nullable NSArray<ANNButtonOptions *> *)parse:(nullable id)buttons;

@end

NS_ASSUME_NONNULL_END
