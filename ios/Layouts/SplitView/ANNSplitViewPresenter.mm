#import "ANNSplitViewPresenter.h"
#import "ANNDefaultOptions.h"
#import "UISplitViewController+ANNOptions.h"
#import "UIViewController+LayoutProtocol.h"

@implementation ANNSplitViewPresenter

// The split view the presenter applies to: a UIKit class, never the module's own.
- (nullable UISplitViewController *)splitView {
    UIViewController *bound = self.boundViewController;
    if (![bound isKindOfClass:UISplitViewController.class] || bound.nodeHelper.destroyed) {
        return nil;
    }
    return (UISplitViewController *)bound;
}

#pragma mark - ANNNodePresenter

// The module passes its own resolution, without the defaults: they come here.
- (void)applyOptionsOnInit:(ANNNavigationOptions *)initialOptions {
    [super applyOptionsOnInit:initialOptions];
    ANNNavigationOptions *resolved = [initialOptions withDefault:ANNDefaultOptions.current];
    [self applyAll:resolved.splitView withChoices:YES];
}

// A screen inside starts to appear: every field again, but the user's choices.
- (void)descendantWillAppear:(UIViewController *)screen {
    [super descendantWillAppear:screen];
    UISplitViewController *splitView = self.splitView;
    if (!splitView) {
        return;
    }
    [self applyAll:[splitView resolveOptionsWithDefault].splitView withChoices:NO];
}

// New default options repaint as an appearance does.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
    [super applyDefaultOptionsChange:resolvedOptions];
    if (!self.splitView) {
        return;
    }
    [self applyAll:resolvedOptions.splitView withChoices:NO];
}

// `mergeOptions` with the split view's id: the fields it names, with the split view's resolution after it.
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [super mergeOptions:mergeOptions resolvedOptions:resolvedOptions];
    if (!self.splitView || !mergeOptions.splitView.hasValue) {
        return;
    }
    [self applyNamed:mergeOptions.splitView values:resolvedOptions.splitView];
}

// A screen inside merged `splitView`: the split view keeps the values in its own options, so that a later apply from
// its resolution has them, and applies the fields the merge names with the merge's values. The merging screen may be
// off the split view's current child (the primary while the secondary shows).
- (void)mergeChildOptions:(ANNNavigationOptions *)mergeOptions {
    [super mergeChildOptions:mergeOptions];
    UISplitViewController *splitView = self.splitView;
    ANNSplitViewOptions *merge = mergeOptions.splitView;
    if (!splitView || !merge.hasValue) {
        return;
    }
    [splitView changeOwnOptions:^(ANNNavigationOptions *options) {
      [options.splitView mergeOptions:merge];
    }];
    ANNSplitViewOptions *values = [[splitView resolveOptionsWithDefault].splitView copy];
    [values mergeOptions:merge];
    [self applyNamed:merge values:values];
}

// Only the split view acts on `splitView`: its parents get the merge without it.
- (ANNNavigationOptions *)optionsForParent:(ANNNavigationOptions *)mergeOptions {
    if (!mergeOptions.splitView.hasValue) {
        return mergeOptions;
    }
    ANNNavigationOptions *up = [mergeOptions copy];
    up.splitView = [ANNSplitViewOptions new];
    return up;
}

#pragma mark - Applying

// Every field; `displayMode` and `inspector.visible` only with `choices` (at creation, without animation).
- (void)applyAll:(ANNSplitViewOptions *)options withChoices:(BOOL)choices {
    UISplitViewController *splitView = self.splitView;
    if (!splitView) {
        return;
    }
    if (choices) {
        [splitView ann_setDisplayMode:options.displayMode];
    }
    [splitView ann_setPrimaryEdge:options.primaryEdge];
    [splitView ann_setMinWidth:options.minWidth];
    [splitView ann_setMaxWidth:options.maxWidth];
    [splitView ann_setPrimaryBackgroundStyle:options.primaryBackgroundStyle];
    [splitView ann_setBehavior:options];
    [splitView ann_setSupplementaryColumn:options.supplementary];
    [splitView ann_setInspectorColumn:options.inspector];
    if (choices) {
        [splitView ann_setInspectorVisible:options.inspector.visible animated:NO];
    }
}

// The fields `named` gives a value, each with its value in `values`. The category applies every field of a group
// that has a value, so the groups it gets hold only the named ones; `displayMode` and `primaryEdge` reset without a
// value, so they are called only when named.
- (void)applyNamed:(ANNSplitViewOptions *)named values:(ANNSplitViewOptions *)values {
    UISplitViewController *splitView = self.splitView;
    if (!splitView) {
        return;
    }
    if (named.displayMode) {
        [splitView ann_setDisplayMode:values.displayMode];
    }
    if (named.primaryEdge) {
        [splitView ann_setPrimaryEdge:values.primaryEdge];
    }
    if (named.minWidth.hasValue) {
        [splitView ann_setMinWidth:values.minWidth];
    }
    if (named.maxWidth.hasValue) {
        [splitView ann_setMaxWidth:values.maxWidth];
    }
    if (named.primaryBackgroundStyle) {
        [splitView ann_setPrimaryBackgroundStyle:values.primaryBackgroundStyle];
    }
    ANNSplitViewOptions *behavior = [ANNSplitViewOptions new];
    if (named.width.hasValue) {
        behavior.width = values.width;
    }
    if (named.splitBehavior) {
        behavior.splitBehavior = values.splitBehavior;
    }
    if (named.displayModeButton) {
        behavior.displayModeButton = values.displayModeButton;
    }
    if (named.presentsWithGesture.hasValue) {
        behavior.presentsWithGesture = values.presentsWithGesture;
    }
    if (named.showsSecondaryOnlyButton.hasValue) {
        behavior.showsSecondaryOnlyButton = values.showsSecondaryOnlyButton;
    }
    if (behavior.hasValue) {
        [splitView ann_setBehavior:behavior];
    }
    ANNSplitViewColumnOptions *supplementary = [self column:[ANNSplitViewColumnOptions new]
                                                      named:named.supplementary
                                                     values:values.supplementary];
    if (supplementary.hasValue) {
        [splitView ann_setSupplementaryColumn:supplementary];
    }
    ANNSplitViewColumnOptions *inspector = [self column:[ANNSplitViewColumnOptions new]
                                                  named:named.inspector
                                                 values:values.inspector];
    if (inspector.hasValue) {
        [splitView ann_setInspectorColumn:inspector];
    }
    if (named.inspector.visible.hasValue) {
        [splitView ann_setInspectorVisible:values.inspector.visible animated:YES];
    }
}

// The widths of a column that `named` gives a value, with their values in `values`.
- (ANNSplitViewColumnOptions *)column:(ANNSplitViewColumnOptions *)column
                                named:(ANNSplitViewColumnOptions *)named
                               values:(ANNSplitViewColumnOptions *)values {
    if (named.width.hasValue) {
        column.width = values.width;
    }
    if (named.minWidth.hasValue) {
        column.minWidth = values.minWidth;
    }
    if (named.maxWidth.hasValue) {
        column.maxWidth = values.maxWidth;
    }
    return column;
}

@end
