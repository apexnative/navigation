#import "ANNSlotComponentView.h"
#import "ANNSlotContainers.h"
#import "ANNSlotContentView.h"
#import "ANNSlotHost.h"
#import "../../cpp/react/renderer/components/ApexNavigationSpec/ANNSlotComponentDescriptor.h"
#import <React/RCTConversions.h>
#import <React/RCTFabricSurface.h>

using namespace facebook::react;

@interface ANNSlotComponentView () <ANNSlotView>
@end

@implementation ANNSlotComponentView {
    // The React children live here. It waits hidden inside the slot, then moves into the container.
    ANNSlotContentView *_contentView;
    ANNSlotShadowNode::ConcreteState::Shared _state;
    // The layout that measures the content was asked for once.
    BOOL _measureAsked;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider {
    return concreteComponentDescriptorProvider<ANNSlotComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        static const auto defaultProps = std::make_shared<const ANNSlotProps>();
        _props = defaultProps;
        // The children belong to the surface of the app's tree, wherever the content view shows them.
        _contentView = [[ANNSlotContentView alloc] initWithSurface:ANNSlotHost.surface];
        _contentView.hidden = YES;
        _contentView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    }
    return self;
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps {
    [super updateProps:props oldProps:oldProps];
    [self attachContent];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState {
    _state = std::static_pointer_cast<const ANNSlotShadowNode::ConcreteState>(state);
    [self attachContent];
}

#pragma mark - ANNSlotView

- (void)slotContainerDidChange:(NSString *)slotId {
    if ([slotId isEqualToString:self.nativeId]) {
        [self attachContent];
    }
}

#pragma mark - Content

- (void)attachContent {
    // The view hears the changes of the container under the slot id it has now, and no other.
    if (self.nativeId != nil) {
        [ANNSlotContainers registerSlotView:self forSlotId:self.nativeId];
    } else {
        [ANNSlotContainers unregisterSlotView:self];
    }
    UIView *container = self.nativeId ? [ANNSlotContainers containerForSlotId:self.nativeId] : nil;
    UIView *target = container ?: self;
    if (_contentView.superview != target) {
        _contentView.frame = target.bounds;
        [target addSubview:_contentView];
    }
    _contentView.hidden = container == nil;
    [self contentDidMount];

    // Fabric lays the children out at the size in the state (ANNSlotComponentDescriptor::adopt). A container that
    // measures the content gets one layout more for it, with the same size, until the height came.
    if (container != nil && _state != nullptr) {
        auto size = RCTSizeFromCGSize(container.bounds.size);
        std::string slotId = self.nativeId.UTF8String ?: "";
        BOOL measures = !_measureAsked && !slotId.empty() &&
                        ANNSlotContentHeights::isRequested(slotId) &&
                        ANNSlotContentHeights::height(slotId) < 0;
        if (size.width > 0 && size.height > 0 && (!(size == _state->getData().size) || measures)) {
            _measureAsked = _measureAsked || measures;
            _state->updateState(ANNSlotState{size});
        }
    }
}

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index {
    [_contentView insertSubview:childComponentView atIndex:index];
    [self contentDidMount];
}

// The container learns once it shows the content.
- (void)contentDidMount {
    UIView *container = _contentView.superview;
    if (container != self && _contentView.subviews.count > 0 &&
        [container conformsToProtocol:@protocol(ANNSlotContainer)]) {
        [(id<ANNSlotContainer>)container slotContentDidMount];
    }
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index {
    [childComponentView removeFromSuperview];
}

// The slot itself stays in the host with nothing to touch.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    return nil;
}

// A recycled slot waits for another screen: it leaves its slot id and drops the old screen's state. Its new props put
// it under the new id.
- (void)prepareForRecycle {
    [super prepareForRecycle];
    [ANNSlotContainers unregisterSlotView:self];
    _measureAsked = NO;
    _state.reset();
    [_contentView removeFromSuperview];
    _contentView.hidden = YES;
}

// An unmounted slot that Fabric doesn't keep for reuse leaves its slot id too.
- (void)invalidate {
    [super invalidate];
    [ANNSlotContainers unregisterSlotView:self];
}

@end
