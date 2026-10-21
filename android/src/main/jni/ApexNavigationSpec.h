#pragma once

// The app's autolinking includes this header instead of the generated one: with the component descriptors of the
// slot and the safe area view, which the generated header leaves out for components with C++ of their own.

#include <ReactCommon/JavaTurboModule.h>
#include <ReactCommon/TurboModule.h>
#include <jsi/jsi.h>
#include <react/renderer/components/ApexNavigationSpec/ANNSafeAreaViewComponentDescriptor.h>
#include <react/renderer/components/ApexNavigationSpec/ANNSlotComponentDescriptor.h>

namespace facebook::react {

JSI_EXPORT
std::shared_ptr<TurboModule> ApexNavigationSpec_ModuleProvider(const std::string &moduleName,
                                                         const JavaTurboModule::InitParams &params);

} // namespace facebook::react
