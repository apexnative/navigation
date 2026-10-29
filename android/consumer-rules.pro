# The native screens `npx apex-navigation codegen` lists in the app: the navigation finds the list by its name.
-keep class dev.apexnative.navigation.generated.ApexNavigationGeneratedScreens { *; }

# The library's C++ measures a React root's content through the surface's handler, which it reads by this getter's
# name (`android/src/main/jni/ANNContentHeights.cpp`).
-keepclassmembers class com.facebook.react.runtime.ReactSurfaceImpl {
    com.facebook.react.fabric.SurfaceHandlerBinding getSurfaceHandler();
}
