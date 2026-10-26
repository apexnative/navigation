#import "ANNFrameDriver.h"
#import <UIKit/UIKit.h>

@class ANNElementTransitionOptions;
@class ANNViewAnimationOptions;

NS_ASSUME_NONNULL_BEGIN

/// The perspective of the tilts `rotationX` and `rotationY` on iOS: the distance d of m34 = −1/d, in points, that React
/// Native's calibration gives Android's default camera (1280 / (screen scale × √5)).
FOUNDATION_EXPORT CGFloat ANNCameraDistance(void);

/// The animated properties of one view: a side of a screen, of the root or of the snapshot, an element, or the fade of
/// a screen. The properties are independent, as on Android: the scale and the rotations go about the view's center (its
/// anchor), the tilts with the perspective of `ANNCameraDistance`, and the translation (`translationX/Y`, or `x − x₀`,
/// `y − y₀`) comes last, neither scaled nor rotated. One order on both platforms (React Native's): scale, `rotationX`,
/// `rotationY`, `rotation` (clockwise, degrees), the perspective, the translation.
///
/// A missing end is the view's value when the tracks are made: `x`, `y` its place in its parent (from its layer's
/// position and bounds, never its frame) moved by its own translation, `alpha` its alpha before any hide, the
/// translations, scales and rotations those of its own transform (React Native's `transform`, split into its parts as
/// Android's views hold it: the shorter angle, −180…180). A property that doesn't play keeps the view's own value, its
/// skew and perspective stay. Each frame writes only what plays: `alpha` only with an `alpha` track, `layer.transform`
/// only when a property of the transform plays; with `translationX` and `x` on one side the translation wins. The frame
/// is never changed (React keeps the layout). `restore` puts back the alpha and the transform as they were read.
@interface ANNViewTracks : NSObject <ANNFrameClient>

/// The playing properties of `side` on `view`. `alphaBefore`: the view's alpha before the core hid it for the
/// transition (the arriving screen), else its alpha now; it is also what `restore` puts back.
- (instancetype)initWithView:(UIView *)view
                        side:(ANNViewAnimationOptions *)side
                 alphaBefore:(CGFloat)alphaBefore;

/// The playing properties of an element (`elementTransitions[]`): appearing from `from` to the view's value,
/// disappearing from it to `to`, or both ends given.
- (instancetype)initWithView:(UIView *)view element:(ANNElementTransitionOptions *)element;

/// A linear fade of `view`'s alpha (a dismissed popover with nothing to play, a transition under Reduce Motion);
/// `restore` puts back `alphaBefore`.
- (instancetype)initWithView:(UIView *)view
                    fadeFrom:(CGFloat)from
                          to:(CGFloat)to
                    duration:(NSTimeInterval)duration
                 alphaBefore:(CGFloat)alphaBefore;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, readonly) UIView *view;

/// At least one property plays.
@property(nonatomic, readonly) BOOL hasTracks;

/// An `alpha` track plays: without one the view keeps the alpha it has (the arriving screen gets its own back at the
/// start).
@property(nonatomic, readonly) BOOL animatesAlpha;

@end

NS_ASSUME_NONNULL_END
