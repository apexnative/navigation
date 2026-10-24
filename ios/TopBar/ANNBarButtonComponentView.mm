#import "ANNBarButtonComponentView.h"
#import "ANNTabsDesign.h"
#import "ANNUtils.h"

static NSString *const ANNBarButtonComponentMountKey = @"ANNBarButtonComponentView";
static const CGFloat ANNBarButtonSlotHeight = 44;
static const CGFloat ANNBarButtonSlotMinimumWidth = 44;
static const CGFloat ANNBarButtonWidthTolerance = 0.5;

// The slot of iOS 26's bar: unless UIKit keeps the earlier design for the app (`ANNTabsDesign`).
static BOOL ANNUsesSlotConstraints(void) {
    return ANNTabsDesign.isGlass;
}

@implementation ANNBarButtonComponentView {
    NSLayoutConstraint *_width;
    NSLayoutConstraint *_height;
    BOOL _usesConstraints;
}

- (instancetype)initWithContent:(UIView<ANNComponentProtocol> *)content {
    if (self = [super initWithFrame:CGRectMake(0, 0, ANNBarButtonSlotMinimumWidth,
                                               ANNBarButtonSlotHeight)]) {
        _content = content;
        self.backgroundColor = UIColor.clearColor;
        content.autoresizingMask =
            UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        content.frame = self.bounds;
        [self addSubview:content];
        _usesConstraints = ANNUsesSlotConstraints();
        if (_usesConstraints) {
            self.translatesAutoresizingMaskIntoConstraints = NO;
            _width = [self.widthAnchor constraintEqualToConstant:ANNBarButtonSlotMinimumWidth];
            _height = [self.heightAnchor constraintEqualToConstant:ANNBarButtonSlotHeight];
            _width.active = YES;
            _height.active = YES;
        }
        __weak ANNBarButtonComponentView *weakSelf = self;
        [content setContentDidMount:^{
          [weakSelf measure];
        }
                             forKey:ANNBarButtonComponentMountKey];
        [self measure];
    }
    return self;
}

- (void)measure {
    RCTFabricSurface *surface = _content.surface;
    if (!surface || !_content.isReady) {
        return;
    }
    CGFloat maxWidth = ANNUtils.mainScreen.bounds.size.width;
    CGSize size = [surface sizeThatFitsMinimumSize:CGSizeZero
                                       maximumSize:CGSizeMake(maxWidth, ANNBarButtonSlotHeight)];
    CGFloat width = ceil(size.width);
    if (_usesConstraints) {
        width = MAX(width, ANNBarButtonSlotMinimumWidth);
        if (fabs(width - _measuredWidth) <= ANNBarButtonWidthTolerance) {
            return;
        }
        _measuredWidth = width;
        _width.constant = width;
        [self invalidateIntrinsicContentSize];
        [self invalidateHost];
        return;
    }
    CGFloat height = ceil(size.height);
    if (fabs(width - _measuredWidth) <= ANNBarButtonWidthTolerance &&
        fabs(height - self.bounds.size.height) <= ANNBarButtonWidthTolerance) {
        return;
    }
    _measuredWidth = width;
    self.frame = CGRectMake(self.frame.origin.x, self.frame.origin.y, width, height);
    [self invalidateIntrinsicContentSize];
    [self invalidateHost];
}

// A new width reaches the bar only when the bar lays its items out again: the wrapper UIKit puts around the custom
// view and the bar (or toolbar) that places the items and keeps the edge margin. Nothing while the slot isn't in one.
- (void)invalidateHost {
    UIView *wrapper = self.superview;
    [wrapper setNeedsLayout];
    for (UIView *view = wrapper; view; view = view.superview) {
        if ([view isKindOfClass:UINavigationBar.class] || [view isKindOfClass:UIToolbar.class]) {
            [view setNeedsLayout];
            return;
        }
    }
}

- (CGSize)intrinsicContentSize {
    if (_usesConstraints) {
        return CGSizeMake(_width.constant, ANNBarButtonSlotHeight);
    }
    return self.bounds.size;
}

- (CGSize)sizeThatFits:(CGSize)size {
    return [self intrinsicContentSize];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    if (!CGRectEqualToRect(_content.frame, self.bounds)) {
        _content.frame = self.bounds;
    }
}

@end
