#import "ANNToolbarPresenter.h"
#import "ANNComponentViewController.h"

@implementation ANNToolbarPresenter

+ (void)applyOptions:(ANNNavigationOptions *)options
            ofScreen:(UIViewController *)screen
             buttons:(ANNBarButtons *)buttons
            animated:(BOOL)animated {
    NSArray<UIBarButtonItem *> *items = [buttons toolbarItemsWithOptions:options];
    [screen setToolbarItems:items.count > 0 ? items : nil animated:animated];
    // The pair hears that its screen set its toolbar items (the module's delegate).
    if ([screen isKindOfClass:ANNComponentViewController.class]) {
        ANNComponentViewController *component = (ANNComponentViewController *)screen;
        [component.arrangement screen:component didSetToolbarItemsAnimated:animated];
    }
}

+ (BOOL)mergeChangesToolbar:(ANNNavigationOptions *)mergeOptions {
    ANNToolbarOptions *toolbar = mergeOptions.toolbar;
    return toolbar.buttons != nil || toolbar.visible.hasValue || toolbar.testID.hasValue;
}

@end
