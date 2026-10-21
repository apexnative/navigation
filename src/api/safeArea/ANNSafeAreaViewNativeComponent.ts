import {
  codegenNativeComponent,
  type CodegenTypes,
  type HostComponent,
  type ViewProps,
} from 'react-native';

export interface NativeProps extends ViewProps {
  /** The screen whose safe area the view keeps its content out of. */
  screenId: string;
  /** Once: the view is laid out in its place, with a size. */
  onReady?: CodegenTypes.DirectEventHandler<null>;
  /**
   * The screen is rendered into the view, also when it renders nothing (`null`): what a transition into the screen
   * waits for.
   */
  contentRendered?: CodegenTypes.WithDefault<boolean, false>;
  /**
   * `[top, right, bottom, left]`: the padding of a screen laid out before its push (Android), so the first layout
   * already has it. Empty: the native side gives it once the screen is laid out.
   */
  initialPadding?: ReadonlyArray<CodegenTypes.Float>;
}

// The C++ of the component is the library's own: its state holds the padding the native side gives it.
export default codegenNativeComponent<NativeProps>('ANNSafeAreaView', {
  interfaceOnly: true,
}) as HostComponent<NativeProps>;
