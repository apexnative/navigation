/**
 * React Native CLI configuration of the library.
 *
 * `spm.name` is the SwiftPM target name in apps on React Native's SwiftPM setup; it matches the CocoaPods
 * module, so apps `import ApexNavigation` either way.
 */
module.exports = {
  spm: {
    name: 'ApexNavigation',
  },
  dependency: {
    platforms: {
      // The slot and the safe area view have C++ of their own (their states hold the size of a container, a
      // padding): the library builds it with its codegen and registers their descriptors with the app.
      android: {
        libraryName: 'ApexNavigationSpec',
        componentDescriptors: ['ANNSlotComponentDescriptor', 'ANNSafeAreaViewComponentDescriptor'],
        cmakeListsPath: 'src/main/jni/CMakeLists.txt',
      },
    },
  },
};
