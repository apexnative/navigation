#pragma once

#include "ANNSlotShadowNode.h"
#include <react/renderer/core/ConcreteComponentDescriptor.h>

namespace facebook::react {

/** Lays a slot out at the size of its container, which the native side keeps in the slot's state. */
class ANNSlotComponentDescriptor final : public ConcreteComponentDescriptor<ANNSlotShadowNode> {
  public:
    using ConcreteComponentDescriptor::ConcreteComponentDescriptor;

    void adopt(ShadowNode &shadowNode) const override {
        auto &layoutableShadowNode = static_cast<YogaLayoutableShadowNode &>(shadowNode);
        auto &state = static_cast<const ANNSlotShadowNode::ConcreteState &>(*shadowNode.getState()).getData();
        layoutableShadowNode.setSize(state.size);
        layoutableShadowNode.setPositionType(YGPositionTypeAbsolute);
        ConcreteComponentDescriptor::adopt(shadowNode);
    }
};

} // namespace facebook::react
