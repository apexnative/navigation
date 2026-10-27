// The height of React content laid out at a width without a height limit, for the Kotlin side
// (`dev.apexnative.navigation.react.ContentHeights`): a sheet with the 'content' detent. Fabric lays out a copy of the
// tree for it, so the shown layout stays as it is: a React root's surface here, a slot's content in the slot's own
// layout (`ANNSlotShadowNode`), which keeps the height in `ANNSlotContentHeights`.

#include <fbjni/fbjni.h>
#include <jni.h>
#include <react/fabric/SurfaceHandlerBinding.h>
#include <react/renderer/components/ApexNavigationSpec/ANNSlotState.h>
#include <react/renderer/core/LayoutConstraints.h>
#include <react/renderer/core/LayoutContext.h>

#include <limits>

using namespace facebook;

namespace {

std::string slotIdOf(jstring slotId) {
    return slotId ? jni::wrap_alias(slotId)->toStdString() : std::string{};
}

// A React root's surface: React Native keeps the getter of its surface handler unmangled for the native side.
struct JReactSurfaceImpl : jni::JavaClass<JReactSurfaceImpl> {
    static constexpr auto kJavaDescriptor = "Lcom/facebook/react/runtime/ReactSurfaceImpl;";

    const react::SurfaceHandler &surfaceHandler() const {
        static const auto method =
            javaClassStatic()->getMethod<react::SurfaceHandlerBinding::javaobject()>("getSurfaceHandler");
        return method(self())->cthis()->getSurfaceHandler();
    }
};

} // namespace

/**
 * The height of the surface's content at [width] points without a height limit, in points: `SurfaceHandler::measure`,
 * as iOS's surface measures for `sizeThatFits`. 0 for a surface that doesn't run; -1 on a failure.
 */
extern "C" JNIEXPORT jfloat JNICALL Java_dev_apexnative_navigation_react_ContentHeights_nativeSurfaceHeight(
    JNIEnv * /*env*/, jclass /*type*/, jobject surface, jfloat width, jfloat pointScale, jfloat fontScale, jboolean rtl,
    jboolean swapLeftAndRight) {
    try {
        auto ref = jni::wrap_alias(static_cast<JReactSurfaceImpl::javaobject>(surface));
        react::LayoutConstraints constraints{};
        constraints.minimumSize = react::Size{.width = width, .height = 0};
        constraints.maximumSize = react::Size{.width = width, .height = std::numeric_limits<react::Float>::infinity()};
        constraints.layoutDirection = rtl ? react::LayoutDirection::RightToLeft : react::LayoutDirection::LeftToRight;
        react::LayoutContext context{};
        context.pointScaleFactor = pointScale;
        context.fontSizeMultiplier = fontScale;
        context.swapLeftAndRightInRTL = swapLeftAndRight;
        return ref->surfaceHandler().measure(constraints, context).height;
    } catch (...) {
        jni::translatePendingCppExceptionToJavaException();
        return -1;
    }
}

/** The slot's content is measured from its next layout on (`ANNSlotContentHeights::request`). */
extern "C" JNIEXPORT void JNICALL Java_dev_apexnative_navigation_react_ContentHeights_nativeRequestSlot(
    JNIEnv * /*env*/, jclass /*type*/, jstring slotId) {
    react::ANNSlotContentHeights::request(slotIdOf(slotId));
}

/** Whether the slot's content is measured. */
extern "C" JNIEXPORT jboolean JNICALL Java_dev_apexnative_navigation_react_ContentHeights_nativeIsSlotRequested(
    JNIEnv * /*env*/, jclass /*type*/, jstring slotId) {
    return react::ANNSlotContentHeights::isRequested(slotIdOf(slotId));
}

/** The height of the slot's content at its last layout, in points; negative before it. */
extern "C" JNIEXPORT jfloat JNICALL Java_dev_apexnative_navigation_react_ContentHeights_nativeSlotHeight(
    JNIEnv * /*env*/, jclass /*type*/, jstring slotId) {
    return react::ANNSlotContentHeights::height(slotIdOf(slotId));
}

/** The slot's screen went: nothing more is measured for it. */
extern "C" JNIEXPORT void JNICALL Java_dev_apexnative_navigation_react_ContentHeights_nativeForgetSlot(
    JNIEnv * /*env*/, jclass /*type*/, jstring slotId) {
    react::ANNSlotContentHeights::forget(slotIdOf(slotId));
}
