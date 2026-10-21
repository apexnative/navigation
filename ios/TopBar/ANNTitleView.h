#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The title with a subtitle before iOS 26 (`topBar.subtitle.text`): two centred labels in the item's `titleView`, the
/// title 16 pt semibold and the subtitle 12 pt by default, the options' fonts and colors over them; each label its own
/// color. Ids `<topBar.testID>.title` and `.subtitle`.
@interface ANNTitleView : UIView

- (instancetype)initWithOptions:(ANNNavigationOptions *)options;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
