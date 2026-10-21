#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `preview` (`PreviewOptions`): the preview of a pushed screen, set by `usePreview` (`ANNPreviewInteraction`).
@interface ANNPreviewOptions : ANNOptions

@property(nonatomic, strong) Text *nativeID;
@property(nonatomic, strong) Bool *commit;
/// The menu items as JS gives them: `{ id, title, style?, actions? }`; nil without the option.
@property(nonatomic, strong, nullable) NSArray<NSDictionary *> *actions;
@property(nonatomic, strong) Double *width;
@property(nonatomic, strong) Double *height;

@end

NS_ASSUME_NONNULL_END
