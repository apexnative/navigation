#import "UIView+Utils.h"

@implementation UIView (Utils)

- (UIView *)findDescendantByClass:(Class)viewClass {
    if (!viewClass) {
        return nil;
    }
    for (UIView *subview in self.subviews) {
        if ([subview isKindOfClass:viewClass]) {
            return subview;
        }
        UIView *found = [subview findDescendantByClass:viewClass];
        if (found) {
            return found;
        }
    }
    return nil;
}

@end
