#pragma once

#include <react/renderer/graphics/RectangleEdges.h>

#ifdef RN_SERIALIZABLE_STATE
#include <folly/dynamic.h>
#endif

namespace facebook::react {

/** The padding a safe area view keeps its content in by: the safe area of its screen on the edges it covers. */
class ANNSafeAreaViewState final {
  public:
    ANNSafeAreaViewState() = default;
    explicit ANNSafeAreaViewState(EdgeInsets padding_) : padding(padding_) {}

#ifdef RN_SERIALIZABLE_STATE
    ANNSafeAreaViewState(const ANNSafeAreaViewState & /*previousState*/, folly::dynamic data)
        : padding(EdgeInsets{.left = (Float)data["left"].getDouble(),
                             .top = (Float)data["top"].getDouble(),
                             .right = (Float)data["right"].getDouble(),
                             .bottom = (Float)data["bottom"].getDouble()}) {}

    folly::dynamic getDynamic() const {
        return folly::dynamic::object("left", padding.left)("top", padding.top)("right", padding.right)(
            "bottom", padding.bottom);
    }
#endif

    EdgeInsets padding{};
};

} // namespace facebook::react
