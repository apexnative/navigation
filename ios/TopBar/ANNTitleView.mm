#import "ANNTitleView.h"

static const CGFloat ANNTitleViewTitleSize = 16;
static const CGFloat ANNTitleViewSubtitleSize = 12;

static UILabel *ANNTitleLabel(NSString *text, ANNFontOptions *font, Color *color,
                              UIFont *placeFont) {
    UILabel *label = [UILabel new];
    label.text = text;
    label.textAlignment = NSTextAlignmentCenter;
    label.font = [font fontWithPlaceFont:placeFont] ?: placeFont;
    label.textColor = color.hasValue && !color.isNoColor ? [color withDefault:UIColor.labelColor]
                                                         : UIColor.labelColor;
    label.lineBreakMode = NSLineBreakByTruncatingTail;
    [label sizeToFit];
    return label;
}

@implementation ANNTitleView {
    UILabel *_title;
    UILabel *_subtitle;
}

- (instancetype)initWithOptions:(ANNNavigationOptions *)options {
    if (self = [super initWithFrame:CGRectZero]) {
        ANNTopBarOptions *topBar = options.topBar;
        UIFont *titleFont = [UIFont systemFontOfSize:ANNTitleViewTitleSize
                                              weight:UIFontWeightSemibold];
        _title = ANNTitleLabel([topBar.title.text withDefault:nil] ?: @"", topBar.title.font,
                               topBar.title.color, titleFont);
        _subtitle = ANNTitleLabel([topBar.subtitle.text withDefault:nil] ?: @"",
                                  topBar.subtitle.font, topBar.subtitle.color,
                                  [UIFont systemFontOfSize:ANNTitleViewSubtitleSize]);
        NSString *testID = [topBar.testID withDefault:nil];
        if (testID.length > 0) {
            _title.accessibilityIdentifier = [testID stringByAppendingString:@".title"];
            _subtitle.accessibilityIdentifier = [testID stringByAppendingString:@".subtitle"];
        }
        [self addSubview:_title];
        [self addSubview:_subtitle];
        CGSize size = [self intrinsicContentSize];
        self.frame = CGRectMake(0, 0, size.width, size.height);
    }
    return self;
}

- (CGSize)intrinsicContentSize {
    CGSize title = _title.bounds.size;
    CGSize subtitle = _subtitle.bounds.size;
    return CGSizeMake(ceil(MAX(title.width, subtitle.width)), ceil(title.height + subtitle.height));
}

- (void)layoutSubviews {
    [super layoutSubviews];
    CGRect bounds = self.bounds;
    CGFloat titleHeight = _title.bounds.size.height;
    CGFloat subtitleHeight = _subtitle.bounds.size.height;
    CGFloat top = MAX(0, (bounds.size.height - titleHeight - subtitleHeight) / 2);
    _title.frame = CGRectMake(0, top, bounds.size.width, titleHeight);
    _subtitle.frame = CGRectMake(0, top + titleHeight, bounds.size.width, subtitleHeight);
}

@end
