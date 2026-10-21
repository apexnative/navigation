#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `IconBackgroundOptions`: a plate behind the icon of a button.
@interface ANNIconBackgroundOptions : ANNOptions

@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) Color *disabledColor;
@property(nonatomic, strong) Double *cornerRadius;
@property(nonatomic, strong) Double *width;
@property(nonatomic, strong) Double *height;

@end

NS_ASSUME_NONNULL_END
