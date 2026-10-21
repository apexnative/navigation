#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `ZoomTransitionOptions`: the zoom of a screen out of the view with `fromId`; `null` takes the zoom away.
@interface ANNZoomTransitionOptions : ANNOptions

@property(nonatomic, strong) Text *fromId;
@property(nonatomic, strong) Bool *enabled;

@end

NS_ASSUME_NONNULL_END
