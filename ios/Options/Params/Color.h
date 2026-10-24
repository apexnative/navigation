#import "Param.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A color of the options: a color, a light and dark pair (UIKit switches it by itself), a pair with one side only, or
/// an explicit "no color". A color replaces the color of the layers under it as a whole.
@interface Color : Param

/// A color the JSON gave; an explicit "no color" is `noColor`.
- (instancetype)initWithColor:(UIColor *)color;

/// A pair for the light and the dark style. With both sides it is one dynamic color; with one side the other style
/// shows the place's own color (`withDefault:`).
- (instancetype)initWithLight:(nullable UIColor *)light dark:(nullable UIColor *)dark;

/// `null` or `'NoColor'`: the place as the platform draws it without the option, over what the layers under it give.
+ (Color *)noColor;

/// The side of a pair for the light style; nil without one, and for a single color.
@property(nonatomic, strong, readonly, nullable) UIColor *light;
/// The side of a pair for the dark style; nil without one, and for a single color.
@property(nonatomic, strong, readonly, nullable) UIColor *dark;

/// The color; nil without one, for "no color", and for a pair with one side (use `withDefault:` there).
- (nullable UIColor *)get;

/// The color, or `defaultValue` without one. A pair with one side gives a dynamic color whose missing side is
/// `defaultValue` (the place's own color, which may be dynamic itself); "no color" gives nil.
- (nullable UIColor *)withDefault:(nullable UIColor *)defaultValue;

/// The color for these traits: the side of a pair (nil where the side is missing), a dynamic color resolved.
- (nullable UIColor *)colorForTraitCollection:(UITraitCollection *)traitCollection;

/// The options gave `null` or `'NoColor'`.
- (BOOL)isNoColor;

/// A pair that has only one of its sides.
- (BOOL)isPartial;

/// The same color: a pair by its sides (its dynamic color is made anew at each read), anything else by its value.
- (BOOL)isSameValueAs:(nullable ANNParam *)other;

@end

NS_ASSUME_NONNULL_END
