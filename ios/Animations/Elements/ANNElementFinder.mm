#import "ANNElementFinder.h"
#import "ANNAppearanceDispatcher.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTViewComponentView.h>

@implementation ANNElementFinder

+ (UIView *)searchRootOf:(UIViewController *)node {
    if (!node) {
        return nil;
    }
    UIViewController *screen = [ANNAppearanceDispatcher visibleScreenOf:node];
    return screen.reactView;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID inView:(UIView *)root {
    if (nativeID.length == 0 || !root) {
        return nil;
    }
    if ([root isKindOfClass:RCTViewComponentView.class] &&
        [((RCTViewComponentView *)root).nativeId isEqualToString:nativeID]) {
        return root;
    }
    for (UIView *subview in root.subviews) {
        UIView *found = [self viewWithNativeID:nativeID inView:subview];
        if (found) {
            return found;
        }
    }
    return nil;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID inNode:(UIViewController *)node {
    return [self viewWithNativeID:nativeID inView:[self searchRootOf:node]];
}

@end
