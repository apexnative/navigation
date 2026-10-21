import {
  codegenNativeComponent,
  type CodegenTypes,
  type HostComponent,
  type ViewProps,
} from 'react-native';

// `nativeID` is the slot id.
export interface NativeProps extends ViewProps {
  /**
   * `[width, height]` in dp: the size of the container its screen was laid out in before the push (Android), so the
   * first layout already has it. Empty: the container gives it once it is laid out.
   */
  initialSize?: ReadonlyArray<CodegenTypes.Float>;
}

/**
 * Shows its children inside the native container with the same slot id (`nativeID`), at the container's size. The
 * children stay in the app's one React tree, so they read its providers.
 */
export default codegenNativeComponent<NativeProps>('ANNSlot', {
  interfaceOnly: true,
}) as HostComponent<NativeProps>;
