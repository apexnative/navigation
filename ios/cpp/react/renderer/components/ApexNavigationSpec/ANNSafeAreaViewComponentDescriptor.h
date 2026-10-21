#pragma once

#include "ANNSafeAreaViewShadowNode.h"
#include <react/renderer/core/ConcreteComponentDescriptor.h>

namespace facebook::react {

/** Lays a safe area view out with the padding the native side keeps in its state. */
class ANNSafeAreaViewComponentDescriptor final : public ConcreteComponentDescriptor<ANNSafeAreaViewShadowNode> {
  public:
    using ConcreteComponentDescriptor::ConcreteComponentDescriptor;

    void adopt(ShadowNode &shadowNode) const override {
        static_cast<ANNSafeAreaViewShadowNode &>(shadowNode).applyPadding();
        ConcreteComponentDescriptor::adopt(shadowNode);
    }
};

} // namespace facebook::react
