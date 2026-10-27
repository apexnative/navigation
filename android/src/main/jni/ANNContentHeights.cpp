// The height of React content laid out at a width without a height limit, for the Kotlin side
// (`dev.apexnative.navigation.react.ContentHeights`): a sheet with the 'content' detent. Fabric lays out a copy of the
// tree for it, so the shown layout stays as it is.

#include <fbjni/fbjni.h>
#include <jni.h>
#include <react/fabric/SurfaceHandlerBinding.h>
#include <react/renderer/core/LayoutConstraints.h>
#include <react/renderer/core/LayoutContext.h>

#include <limits>

using namespace facebook;

namespace {

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
