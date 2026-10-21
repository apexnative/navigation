#import "ANNTitleGlassView.h"

static const CGFloat ANNGlassHorizontalPadding = 16;
static const CGFloat ANNGlassOneLinePadding = 7;
static const CGFloat ANNGlassTwoLinesPadding = 4;

static UILabel *ANNGlassLabel(NSString *text, ANNFontOptions *font, Color *color, UIFont *placeFont,
                              UIColor *placeColor) {
    UILabel *label = [UILabel new];
    label.text = text;
    label.textAlignment = NSTextAlignmentCenter;
    label.font = [font fontWithPlaceFont:placeFont] ?: placeFont;
    label.textColor =
        color.hasValue && !color.isNoColor ? [color withDefault:placeColor] : placeColor;
    [label sizeToFit];
    return label;
}

@implementation ANNTitleGlassView {
    UIVisualEffectView *_effectView;
    UILabel *_title;
    UILabel *_subtitle;
    UIView *_component;
}

+ (BOOL)isGlass:(NSString *)glass {
    return [glass isEqualToString:@"regular"] || [glass isEqualToString:@"clear"];
}

- (instancetype)initWithGlass:(NSString *)glass options:(ANNNavigationOptions *)options {
    if (self = [super initWithFrame:CGRectZero]) {
        [self makeEffect:glass options:options];
        ANNTopBarOptions *topBar = options.topBar;
        NSString *subtitle = [topBar.subtitle.text withDefault:nil];
        BOOL twoLines = subtitle.length > 0;
        UIFont *titleFont = [UIFont systemFontOfSize:twoLines ? 15 : 17
                                              weight:UIFontWeightSemibold];
        _title = ANNGlassLabel([topBar.title.text withDefault:nil] ?: @"", topBar.title.font,
                               topBar.title.color, titleFont, UIColor.labelColor);
        [_effectView.contentView addSubview:_title];
        if (twoLines) {
            _subtitle = ANNGlassLabel(subtitle, topBar.subtitle.font, topBar.subtitle.color,
                                      [UIFont systemFontOfSize:12], UIColor.secondaryLabelColor);
            [_effectView.contentView addSubview:_subtitle];
        }
        [self sizeToContent];
    }
    return self;
}

- (instancetype)initWithGlass:(NSString *)glass
                    component:(UIView *)component
                      options:(ANNNavigationOptions *)options {
    if (self = [super initWithFrame:CGRectZero]) {
        [self makeEffect:glass options:options];
        _component = component;
        [_effectView.contentView addSubview:component];
        [self sizeToContent];
    }
    return self;
}

- (void)makeEffect:(NSString *)glass options:(ANNNavigationOptions *)options {
    UIGlassEffectStyle style =
        [glass isEqualToString:@"clear"] ? UIGlassEffectStyleClear : UIGlassEffectStyleRegular;
    _effectView = [[UIVisualEffectView alloc] initWithEffect:[UIGlassEffect effectWithStyle:style]];
    _effectView.cornerConfiguration = [UICornerConfiguration capsuleConfiguration];
    _effectView.autoresizingMask =
        UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self addSubview:_effectView];
    NSString *testID = [options.topBar.testID withDefault:nil];
    if (testID.length > 0) {
        self.accessibilityIdentifier = [testID stringByAppendingString:@".title"];
    }
}

- (void)componentSizeDidChange {
    [self sizeToContent];
}

- (CGSize)contentSize {
    if (_component) {
        // The size the title asks for (its row with `fill`), not the frame UIKit last gave it.
        return _component.intrinsicContentSize;
    }
    CGSize title = _title.bounds.size;
    CGSize subtitle = _subtitle ? _subtitle.bounds.size : CGSizeZero;
    CGFloat vertical = _subtitle ? ANNGlassTwoLinesPadding : ANNGlassOneLinePadding;
    return CGSizeMake(ceil(MAX(title.width, subtitle.width) + 2 * ANNGlassHorizontalPadding),
                      ceil(title.height + subtitle.height + 2 * vertical));
}

- (void)sizeToContent {
    CGSize size = [self contentSize];
    self.frame = CGRectMake(self.frame.origin.x, self.frame.origin.y, size.width, size.height);
    _effectView.frame = self.bounds;
    [self invalidateIntrinsicContentSize];
    [self setNeedsLayout];
}

- (CGSize)intrinsicContentSize {
    return [self contentSize];
}

- (CGSize)sizeThatFits:(CGSize)size {
    return [self contentSize];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    CGRect bounds = self.bounds;
    _effectView.frame = bounds;
    if (_component) {
        _component.frame = CGRectMake(0, 0, bounds.size.width, bounds.size.height);
        return;
    }
    CGFloat titleHeight = _title.bounds.size.height;
    CGFloat subtitleHeight = _subtitle ? _subtitle.bounds.size.height : 0;
    CGFloat top = MAX(0, (bounds.size.height - titleHeight - subtitleHeight) / 2);
    CGFloat width = MAX(0, bounds.size.width - 2 * ANNGlassHorizontalPadding);
    _title.frame = CGRectMake(ANNGlassHorizontalPadding, top, width, titleHeight);
    _subtitle.frame =
        CGRectMake(ANNGlassHorizontalPadding, top + titleHeight, width, subtitleHeight);
}

@end
