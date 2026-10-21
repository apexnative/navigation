#import "UISplitViewController+ANNOptions.h"

@implementation UISplitViewController (ANNOptions)

// UIKit's display modes, and the names of the modes before the column API: with three columns
// 'visible' shows both the primary and the supplementary column.
- (void)ann_setDisplayMode:(NSString *)displayMode {
    BOOL triple = self.style == UISplitViewControllerStyleTripleColumn;
    NSDictionary<NSString *, NSNumber *> *modes = @{
        @"secondaryOnly" : @(UISplitViewControllerDisplayModeSecondaryOnly),
        @"oneBesideSecondary" : @(UISplitViewControllerDisplayModeOneBesideSecondary),
        @"oneOverSecondary" : @(UISplitViewControllerDisplayModeOneOverSecondary),
        @"twoBesideSecondary" : @(UISplitViewControllerDisplayModeTwoBesideSecondary),
        @"twoOverSecondary" : @(UISplitViewControllerDisplayModeTwoOverSecondary),
        @"twoDisplaceSecondary" : @(UISplitViewControllerDisplayModeTwoDisplaceSecondary),
        @"hidden" : @(UISplitViewControllerDisplayModeSecondaryOnly),
        @"visible" : triple ? @(UISplitViewControllerDisplayModeTwoBesideSecondary)
                            : @(UISplitViewControllerDisplayModeOneBesideSecondary),
        @"overlay" : triple ? @(UISplitViewControllerDisplayModeTwoOverSecondary)
                            : @(UISplitViewControllerDisplayModeOneOverSecondary),
    };
    NSNumber *mode = displayMode ? modes[displayMode] : nil;
    self.preferredDisplayMode = mode ? (UISplitViewControllerDisplayMode)mode.integerValue
                                     : UISplitViewControllerDisplayModeAutomatic;
}

- (void)ann_setPrimaryEdge:(NSString *)primaryEdge {
    if ([primaryEdge isEqualToString:@"trailing"]) {
        self.primaryEdge = UISplitViewControllerPrimaryEdgeTrailing;
    } else {
        self.primaryEdge = UISplitViewControllerPrimaryEdgeLeading;
    }
}

- (void)ann_setMinWidth:(Number *)minWidth {
    if (minWidth.hasValue) {
        self.minimumPrimaryColumnWidth = [[minWidth get] doubleValue];
    }
}

- (void)ann_setMaxWidth:(Number *)maxWidth {
    if (maxWidth.hasValue) {
        self.maximumPrimaryColumnWidth = [[maxWidth get] doubleValue];
    }
}

// `none`, the default, is UIKit's plain background: it takes back what `sidebar` set.
- (void)ann_setPrimaryBackgroundStyle:(NSString *)style {
    self.primaryBackgroundStyle = [style isEqualToString:@"sidebar"]
                                      ? UISplitViewControllerBackgroundStyleSidebar
                                      : UISplitViewControllerBackgroundStyleNone;
}

- (void)ann_setBehavior:(ANNSplitViewOptions *)options {
    if (options.width.hasValue) {
        self.preferredPrimaryColumnWidth = [[options.width get] doubleValue];
    }
    NSDictionary<NSString *, NSNumber *> *behaviors = @{
        @"automatic" : @(UISplitViewControllerSplitBehaviorAutomatic),
        @"tile" : @(UISplitViewControllerSplitBehaviorTile),
        @"overlay" : @(UISplitViewControllerSplitBehaviorOverlay),
        @"displace" : @(UISplitViewControllerSplitBehaviorDisplace),
    };
    NSNumber *behavior = options.splitBehavior ? behaviors[options.splitBehavior] : nil;
    if (behavior) {
        self.preferredSplitBehavior = (UISplitViewControllerSplitBehavior)behavior.integerValue;
    }
    NSDictionary<NSString *, NSNumber *> *buttons = @{
        @"automatic" : @(UISplitViewControllerDisplayModeButtonVisibilityAutomatic),
        @"never" : @(UISplitViewControllerDisplayModeButtonVisibilityNever),
        @"always" : @(UISplitViewControllerDisplayModeButtonVisibilityAlways),
    };
    NSNumber *button = options.displayModeButton ? buttons[options.displayModeButton] : nil;
    if (button) {
        self.displayModeButtonVisibility =
            (UISplitViewControllerDisplayModeButtonVisibility)button.integerValue;
    }
    if (options.presentsWithGesture.hasValue) {
        self.presentsWithGesture = options.presentsWithGesture.get;
    }
    if (options.showsSecondaryOnlyButton.hasValue) {
        self.showsSecondaryOnlyButton = options.showsSecondaryOnlyButton.get;
    }
}

- (void)ann_setSupplementaryColumn:(ANNSplitViewColumnOptions *)options {
    if (options.width.hasValue) {
        self.preferredSupplementaryColumnWidth = [[options.width get] doubleValue];
    }
    if (options.minWidth.hasValue) {
        self.minimumSupplementaryColumnWidth = [[options.minWidth get] doubleValue];
    }
    if (options.maxWidth.hasValue) {
        self.maximumSupplementaryColumnWidth = [[options.maxWidth get] doubleValue];
    }
}

- (void)ann_setInspectorColumn:(ANNSplitViewColumnOptions *)options {
    if (@available(iOS 26.0, *)) {
        if (options.width.hasValue) {
            self.preferredInspectorColumnWidth = [[options.width get] doubleValue];
        }
        if (options.minWidth.hasValue) {
            self.minimumInspectorColumnWidth = [[options.minWidth get] doubleValue];
        }
        if (options.maxWidth.hasValue) {
            self.maximumInspectorColumnWidth = [[options.maxWidth get] doubleValue];
        }
    }
}

- (void)ann_setInspectorVisible:(Bool *)visible animated:(BOOL)animated {
    if (@available(iOS 26.0, *)) {
        if (!visible.hasValue ||
            ![self viewControllerForColumn:UISplitViewControllerColumnInspector]) {
            return;
        }
        void (^change)(void) = ^{
          if (visible.get) {
              [self showColumn:UISplitViewControllerColumnInspector];
          } else {
              [self hideColumn:UISplitViewControllerColumnInspector];
          }
        };
        if (animated) {
            change();
        } else {
            [UIView performWithoutAnimation:change];
        }
    }
}

@end
