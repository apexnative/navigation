#import "ANNTitleComponentView.h"

// The key of the container's handler on the content's mounts.
static NSString *const ANNTitleComponentMountKey = @"ANNTitleComponentView";

@implementation ANNTitleComponentView {
    CGSize _measured;
}

- (instancetype)initWithContent:(UIView<ANNComponentProtocol> *)content {
    if (self = [super initWithFrame:CGRectZero]) {
        _content = content;
        self.backgroundColor = UIColor.clearColor;
        content.autoresizingMask =
            UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        content.frame = self.bounds;
        [self addSubview:content];
        __weak ANNTitleComponentView *weakSelf = self;
        [content setContentDidMount:^{
          [weakSelf measure];
        }
                             forKey:ANNTitleComponentMountKey];
        [self measure];
    }
    return self;
}

- (void)measure {
    if (_fills) {
        return;
    }
    RCTFabricSurface *surface = _content.surface;
    if (!surface) {
        return;
    }
    // A centred title shows at its own size: the content is measured against unbounded space.
    CGSize maximum = CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX);
    CGSize size = [surface sizeThatFitsMinimumSize:CGSizeZero maximumSize:maximum];
    size = CGSizeMake(ceil(size.width), ceil(size.height));
    // A content that has nothing to show for a moment (a render of `null`) keeps the last size, so the title doesn't
    // vanish; a size the view already has doesn't set the view into the item again.
    if (size.width <= 0 || size.height <= 0) {
        return;
    }
    if (!CGSizeEqualToSize(size, _measured)) {
        _measured = size;
        [self invalidateIntrinsicContentSize];
    }
    if (CGSizeEqualToSize(size, self.bounds.size)) {
        return;
    }
    self.frame = CGRectMake(self.frame.origin.x, self.frame.origin.y, size.width, size.height);
    dispatch_block_t sizeDidChange = _sizeDidChange;
    if (sizeDidChange) {
        sizeDidChange();
    }
}

// Only a switch from `fill` to `center` measures again: the same place asked again changes nothing.
- (void)setFills:(BOOL)fills {
    if (_fills == fills) {
        return;
    }
    _fills = fills;
    if (!fills) {
        _rowSize = CGSizeZero;
        [self measure];
    }
}

- (CGSize)intrinsicContentSize {
    return _fills ? _rowSize : _measured;
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
