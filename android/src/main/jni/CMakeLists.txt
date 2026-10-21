cmake_minimum_required(VERSION 3.13)
set(CMAKE_VERBOSE_MAKEFILE ON)

# The library's codegen target with the C++ of its components (the slot's shadow node and descriptor) added. The
# C++ lives under ios/cpp, which the SwiftPM package of the iOS side can reach too.
set(LIB_LITERAL ApexNavigationSpec)
set(LIB_TARGET_NAME react_codegen_${LIB_LITERAL})

set(LIB_ANDROID_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../..)
set(LIB_CPP_DIR ${LIB_ANDROID_DIR}/../ios/cpp)
set(LIB_ANDROID_GENERATED_JNI_DIR ${LIB_ANDROID_DIR}/build/generated/source/codegen/jni)
set(LIB_ANDROID_GENERATED_COMPONENTS_DIR ${LIB_ANDROID_GENERATED_JNI_DIR}/react/renderer/components/${LIB_LITERAL})

file(GLOB LIB_CUSTOM_SRCS CONFIGURE_DEPENDS *.cpp ${LIB_CPP_DIR}/react/renderer/components/${LIB_LITERAL}/*.cpp)
file(GLOB LIB_CODEGEN_SRCS CONFIGURE_DEPENDS ${LIB_ANDROID_GENERATED_JNI_DIR}/*.cpp ${LIB_ANDROID_GENERATED_COMPONENTS_DIR}/*.cpp)

add_library(${LIB_TARGET_NAME} SHARED ${LIB_CUSTOM_SRCS} ${LIB_CODEGEN_SRCS})

target_include_directories(
  ${LIB_TARGET_NAME}
  PUBLIC
  .
  ${LIB_CPP_DIR}
  ${LIB_ANDROID_GENERATED_JNI_DIR}
  ${LIB_ANDROID_GENERATED_COMPONENTS_DIR}
)

target_link_libraries(${LIB_TARGET_NAME} fbjni jsi reactnative)

# The app's autolinking includes <ApexNavigationSpec.h>: this folder's comes first, with the slot's descriptor.
target_include_directories(${CMAKE_PROJECT_NAME} PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_reactnative_options(${LIB_TARGET_NAME} PUBLIC)
