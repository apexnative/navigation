#pragma once

#include "ANNSafeAreaViewState.h"
#include <react/renderer/components/ApexNavigationSpec/EventEmitters.h>
#include <react/renderer/components/ApexNavigationSpec/Props.h>
#include <react/renderer/components/view/ConcreteViewShadowNode.h>
#include <yoga/Yoga.h>

namespace facebook::react {

inline constexpr const char ANNSafeAreaViewComponentName[] = "ANNSafeAreaView";

/** A view that keeps its content out of its screen's safe area: its padding comes from its state, in the layout. */
class ANNSafeAreaViewShadowNode final : public ConcreteViewShadowNode<ANNSafeAreaViewComponentName,
                                                                      ANNSafeAreaViewProps,
                                                                      ANNSafeAreaViewEventEmitter,
                                                                      ANNSafeAreaViewState> {
  public:
    using ConcreteViewShadowNode::ConcreteViewShadowNode;

    /**
     * The first state of a safe area view: the padding in `initialPadding` (`[top, right, bottom, left]`), computed by
     * the native side for the screen before it renders, so the content lays out with it from its first render.
     * Without it the padding stays zero until the native side sets it; a state update from the native side replaces
     * it either way.
     */
    static ANNSafeAreaViewState initialStateData(const Props::Shared &props,
                                                 const ShadowNodeFamily::Shared & /*family*/,
                                                 const ComponentDescriptor & /*descriptor*/) {
        const auto &padding = static_cast<const ANNSafeAreaViewProps &>(*props).initialPadding;
        if (padding.size() != 4) {
            return {};
        }
        return ANNSafeAreaViewState{
            EdgeInsets{.left = padding[3], .top = padding[0], .right = padding[1], .bottom = padding[2]}};
    }

    /** Sets the padding of the state as the padding of the view, which has none of its own. */
    void applyPadding() {
        ensureUnsealed();
        const auto &padding = getStateData().padding;
        using yoga::Edge;
        using yoga::Style;
        auto top = Style::Length::points(padding.top);
        auto left = Style::Length::points(padding.left);
        auto right = Style::Length::points(padding.right);
        auto bottom = Style::Length::points(padding.bottom);
        auto &style = yogaNode_.style();
        if (style.padding(Edge::Top) != top || style.padding(Edge::Left) != left ||
            style.padding(Edge::Right) != right || style.padding(Edge::Bottom) != bottom) {
            style.setPadding(Edge::Top, top);
            style.setPadding(Edge::Left, left);
            style.setPadding(Edge::Right, right);
            style.setPadding(Edge::Bottom, bottom);
            yogaNode_.setDirty(true);
        }
    }
};

} // namespace facebook::react
