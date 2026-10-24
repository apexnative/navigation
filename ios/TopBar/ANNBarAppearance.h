#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What the look of a screen's bar depends on beyond its options: decided by the screen's presenter from its place.
typedef struct {
    /// iOS 26, a large title that collapses into the list can show: the edge look is transparent.
    BOOL transparentEdge;
    /// A large title that doesn't collapse sits under the row (`always`, or `inline` next to a back button): the bar
    /// needs a background, `background.color` or else the system blur (`topBar.largeTitle.collapseOnScroll`).
    BOOL largeTitleNeedsBackground;
} ANNBarAppearanceContext;

/// The look of a screen's bar (`topBar.background.*`, `.scrollEdgeAppearance.*`, `.noBorder`, `.borderColor`, the
/// titles' fonts and colors, the back button's look): one builder fills the screen's own two appearances from its
/// resolved options, whole each time, so the border and the edge survive a merge of the background. The appearances are
/// the screen's: filled, never replaced.
@interface ANNBarAppearance : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// Fills `standard` and `scrollEdge` from `options`. `traits` resolve the colors drawn into images (the 1×1
/// background of iOS 26, in the current style).
+ (void)fillStandard:(UINavigationBarAppearance *)standard
          scrollEdge:(UINavigationBarAppearance *)scrollEdge
         withOptions:(ANNNavigationOptions *)options
             context:(ANNBarAppearanceContext)context
              traits:(UITraitCollection *)traits;

/// The attributes of the title (`topBar.title`): a font only when a font option is set, over UIKit's font of the
/// place; the color when set.
+ (NSDictionary<NSAttributedStringKey, id> *)titleAttributesOf:(ANNNavigationOptions *)options;

/// The attributes of the title at the scroll edge: `scrollEdgeAppearance.title` when set, else the title's.
+ (NSDictionary<NSAttributedStringKey, id> *)scrollEdgeTitleAttributesOf:
    (ANNNavigationOptions *)options;

/// The color of the title for the push of iOS 26: the title's (or the edge title's) color resolved for `traits`, else
/// the label color resolved; a fixed color that doesn't follow the glass.
+ (UIColor *)pinnedTitleColor:(nullable Color *)color traits:(UITraitCollection *)traits;

/// `background.color` as a solid fill of the bar: nil without a color, for "no color" and for a color clear in `traits`
/// (a clear color is a clear bar). The one rule for the bar, its color in the list and the top edge effect.
+ (nullable UIColor *)solidColorOf:(nullable Color *)color traits:(UITraitCollection *)traits;

/// A 1×1 image of `color` resolved for `traits`, with a variant for each style: the same image for the same resolved
/// colors, so a look filled again equals the one before (as the back button's tinted arrow).
+ (UIImage *)imageOfColor:(UIColor *)color traits:(UITraitCollection *)traits;

@end

NS_ASSUME_NONNULL_END
