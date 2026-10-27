#pragma once

#include <react/renderer/graphics/Float.h>
#include <react/renderer/graphics/Size.h>

#include <atomic>
#include <mutex>
#include <string>
#include <unordered_map>

#ifdef RN_SERIALIZABLE_STATE
#include <folly/dynamic.h>
#endif

namespace facebook::react {

/** The size of the native container a slot shows its children in; the native side sets it. */
class ANNSlotState final {
  public:
    ANNSlotState() = default;
    explicit ANNSlotState(Size size_) : size(size_) {}

#ifdef RN_SERIALIZABLE_STATE
    ANNSlotState(const ANNSlotState & /*previousState*/, folly::dynamic data)
        : size(Size{.width = (Float)data["width"].getDouble(), .height = (Float)data["height"].getDouble()}) {}

    folly::dynamic getDynamic() const {
        return folly::dynamic::object("width", size.width)("height", size.height);
    }
#endif

    Size size{};
};

/**
 * The height of the content of the slots the native side measures (a sheet with the `'content'` detent, a modal with
 * `contentSize.height: 'content'`): the native side asks for a slot by its id, and each layout of that slot measures
 * its children at the slot's width without a height limit, through Fabric's own layout of a copy, and keeps the height
 * here. Any thread.
 */
class ANNSlotContentHeights final {
  public:
    /** The slot's content is measured from its next layout on. */
    static void request(const std::string &slotId) {
        std::lock_guard<std::mutex> lock(mutex());
        if (heights().emplace(slotId, Float{-1}).second) {
            requested()++;
        }
    }

    /** Whether the slot's content is measured. */
    static bool isRequested(const std::string &slotId) {
        if (requested().load() == 0) {
            return false;
        }
        std::lock_guard<std::mutex> lock(mutex());
        return heights().count(slotId) > 0;
    }

    /** The height of the slot's last layout; a slot asked for only. */
    static void set(const std::string &slotId, Float height) {
        std::lock_guard<std::mutex> lock(mutex());
        auto entry = heights().find(slotId);
        if (entry != heights().end()) {
            entry->second = height;
        }
    }

    /** The height measured last; negative before the first measure and for a slot not asked for. */
    static Float height(const std::string &slotId) {
        std::lock_guard<std::mutex> lock(mutex());
        auto entry = heights().find(slotId);
        return entry != heights().end() ? entry->second : Float{-1};
    }

    /** The slot's screen went: nothing more is measured for it. */
    static void forget(const std::string &slotId) {
        std::lock_guard<std::mutex> lock(mutex());
        if (heights().erase(slotId) > 0) {
            requested()--;
        }
    }

  private:
    // One store for the whole app, in ANNSlotState.cpp: on Android the slot's layout runs in the app's library and the
    // native side reads the heights from the library's own, and an inline static would give each its own copy.
    static std::mutex &mutex();
    static std::unordered_map<std::string, Float> &heights();
    static std::atomic<int> &requested();
};

} // namespace facebook::react
