#import "ANNBottomTabCellView.h"

// A color as React Native's style takes it from `processColor`: 0xAARRGGBB as a number.
static NSNumber *ANNProcessedColor(UIColor *color, UITraitCollection *traits) {
    CGFloat red = 0, green = 0, blue = 0, alpha = 0;
    [[color resolvedColorWithTraitCollection:traits] getRed:&red
                                                      green:&green
                                                       blue:&blue
                                                      alpha:&alpha];
    uint32_t argb = ((uint32_t)lround(alpha * 255) << 24) | ((uint32_t)lround(red * 255) << 16) |
                    ((uint32_t)lround(green * 255) << 8) | (uint32_t)lround(blue * 255);
    return @(argb);
}

@implementation ANNBottomTabCellView {
    BOOL _selected;
    NSString *_badge;
    BOOL _dot;
    UIColor *_dotColor;
}

- (instancetype)initWithIndex:(NSUInteger)index {
    if (self = [super initWithFrame:CGRectZero]) {
        _index = index;
        self.backgroundColor = UIColor.clearColor;
        self.isAccessibilityElement = NO;
    }
    return self;
}

- (void)setContent:(UIView<ANNComponentProtocol> *)content {
    if (_content == content) {
        return;
    }
    if (_content.superview == self) {
        [_content removeFromSuperview];
    }
    _content = content;
    if (!content) {
        return;
    }
    // The place takes the touches; the React elements inside don't.
    content.userInteractionEnabled = NO;
    content.frame = self.bounds;
    content.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self addSubview:content];
    [self sendProps];
}

- (void)setSelected:(BOOL)selected
              badge:(NSString *)badge
                dot:(BOOL)dot
           dotColor:(UIColor *)dotColor {
    BOOL sameBadge = _badge == badge || [_badge isEqualToString:badge];
    BOOL sameColor = _dotColor == dotColor || [_dotColor isEqual:dotColor];
    if (_selected == selected && sameBadge && _dot == dot && sameColor) {
        return;
    }
    _selected = selected;
    _badge = [badge copy];
    _dot = dot;
    _dotColor = dotColor;
    [self sendProps];
}

// `badge` is absent without a badge, `dotColor` only when the tab sets one.
- (void)sendProps {
    UIView<ANNComponentProtocol> *content = _content;
    if (![content isKindOfClass:ANNReactView.class]) {
        return;
    }
    NSMutableDictionary<NSString *, id> *props = [NSMutableDictionary new];
    props[@"tabIndex"] = @(_index);
    props[@"selected"] = @(_selected);
    props[@"dot"] = @(_dot);
    if (_badge) {
        props[@"badge"] = _badge;
    }
    if (_dotColor) {
        props[@"dotColor"] = ANNProcessedColor(_dotColor, self.traitCollection);
    }
    [(ANNReactView *)content setNativeProps:props];
}

// A dynamic dot color follows the light and dark styles.
- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection {
    [super traitCollectionDidChange:previousTraitCollection];
    if (_dotColor) {
        [self sendProps];
    }
}

- (void)setTestID:(NSString *)testID {
    NSString *identifier = testID.length > 0 ? testID : nil;
    if (![self.accessibilityIdentifier ?: @"" isEqualToString:identifier ?: @""]) {
        self.accessibilityIdentifier = identifier;
    }
}

@end
