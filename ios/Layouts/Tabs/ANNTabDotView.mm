#import "ANNTabDotView.h"
#import "UIView+Utils.h"

@implementation ANNTabDotView

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        self.userInteractionEnabled = NO;
        self.hidden = YES;
        self.layer.masksToBounds = YES;
    }
    return self;
}

- (void)setDiameter:(CGFloat)diameter color:(UIColor *)color {
    CGPoint center = self.center;
    self.bounds = CGRectMake(0, 0, diameter, diameter);
    self.center = center;
    self.layer.cornerRadius = diameter / 2;
    self.backgroundColor = color;
}

// The icon is the button's image view (the module finds it the same way for the centered icons); without one the
// dot sits at the top right of the button's middle.
- (void)placeOverButton:(UIView *)button inBar:(UITabBar *)bar {
    if (!button || button.hidden || !button.window) {
        self.hidden = YES;
        return;
    }
    if (self.superview != bar) {
        [bar addSubview:self];
    }
    [bar bringSubviewToFront:self];
    UIView *icon = [button findDescendantByClass:UIImageView.class];
    CGRect iconFrame =
        icon ? [icon convertRect:icon.bounds toView:bar]
             : [button convertRect:CGRectInset(button.bounds, button.bounds.size.width / 3, 8)
                            toView:bar];
    CGPoint corner = CGPointMake(CGRectGetMaxX(iconFrame), CGRectGetMinY(iconFrame));
    if (!CGPointEqualToPoint(self.center, corner)) {
        self.center = corner;
    }
    self.hidden = NO;
}

@end
