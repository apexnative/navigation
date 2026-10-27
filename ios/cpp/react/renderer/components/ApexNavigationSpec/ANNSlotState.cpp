#include "ANNSlotState.h"

namespace facebook::react {

std::mutex &ANNSlotContentHeights::mutex() {
    static std::mutex value;
    return value;
}

std::unordered_map<std::string, Float> &ANNSlotContentHeights::heights() {
    static std::unordered_map<std::string, Float> value;
    return value;
}

std::atomic<int> &ANNSlotContentHeights::requested() {
    static std::atomic<int> value{0};
    return value;
}

} // namespace facebook::react
