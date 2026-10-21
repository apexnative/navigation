#pragma once

#include "ANNSlotState.h"
#include <react/renderer/components/ApexNavigationSpec/EventEmitters.h>
#include <react/renderer/components/ApexNavigationSpec/Props.h>
#include <react/renderer/components/view/ConcreteViewShadowNode.h>
#include <react/renderer/core/LayoutConstraints.h>
#include <react/renderer/core/LayoutContext.h>

#include <limits>

namespace facebook::react {

inline constexpr const char ANNSlotComponentName[] = "ANNSlot";

/** A slot: its children show in a native container of another screen, at that container's size. */
class ANNSlotShadowNode final
    : public ConcreteViewShadowNode<ANNSlotComponentName, ANNSlotProps, ANNSlotEventEmitter, ANNSlotState> {
  public:
    using ConcreteViewShadowNode::ConcreteViewShadowNode;

    static ShadowNodeTraits BaseTraits() {
        auto traits = ConcreteViewShadowNode::BaseTraits();
        // The children live in another screen: lay them out against the slot, not the host, and never cull them.
        traits.set(ShadowNodeTraits::Trait::RootNodeKind);
        traits.set(ShadowNodeTraits::Trait::Unstable_uncullableView);
        return traits;
    }

    /**
     * The first state of a slot: the size in `initialSize` (`[width, height]`), measured by the native side for the
     * container before the slot renders, so the first layout of the children already has it. Without it the size
     * stays empty until the native side sets it; a state update from the native side replaces it either way.
     */
    static ANNSlotState initialStateData(const Props::Shared &props,
                                         const ShadowNodeFamily::Shared & /*family*/,
                                         const ComponentDescriptor & /*descriptor*/) {
        const auto &size = static_cast<const ANNSlotProps &>(*props).initialSize;
        if (size.size() != 2) {
            return {};
        }
        return ANNSlotState{Size{.width = size[0], .height = size[1]}};
    }

    /**
     * The slot's layout; for a slot the native side measures (`ANNSlotContentHeights`), also the height of its
     * children laid out at its width without a height limit: a copy of the slot, its height left to its content, laid
     * out by Fabric as a measure is (`LayoutableShadowNode::measure`), so the shown tree is not touched. A child that
     * fills the slot (`flex: 1`) counts with the height of its content.
     */
    void layout(LayoutContext layoutContext) override {
        ConcreteViewShadowNode::layout(layoutContext);
        const auto &slotId = getConcreteProps().nativeId;
        if (slotId.empty() || measuring() || !ANNSlotContentHeights::isRequested(slotId)) {
            return;
        }
        Float width = getLayoutMetrics().frame.size.width;
        if (!(width > 0)) {
            return;
        }
        measuring() = true;
        auto copy = std::static_pointer_cast<ANNSlotShadowNode>(clone({}));
        auto style = copy->yogaNode_.style();
        style.setDimension(yoga::Dimension::Width, yoga::StyleSizeLength::points(width));
        style.setDimension(yoga::Dimension::Height, yoga::StyleSizeLength::ofAuto());
        copy->yogaNode_.setStyle(style);
        copy->yogaNode_.setDirty(true);
        LayoutConstraints constraints{};
        constraints.minimumSize = Size{.width = width, .height = 0};
        constraints.maximumSize = Size{.width = width, .height = std::numeric_limits<Float>::infinity()};
        constraints.layoutDirection = getLayoutMetrics().layoutDirection;
        auto context = layoutContext;
        context.affectedNodes = nullptr;
        copy->layoutTree(context, constraints);
        measuring() = false;
        ANNSlotContentHeights::set(slotId, copy->getLayoutMetrics().frame.size.height);
    }

  private:
    // The copy's own layout runs inside the measure: it measures nothing again.
    static bool &measuring() {
        static thread_local bool value = false;
        return value;
    }
};

} // namespace facebook::react
