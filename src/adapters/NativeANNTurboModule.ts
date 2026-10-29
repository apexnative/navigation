import { TurboModuleRegistry, type CodegenTypes, type TurboModule } from 'react-native';

/**
 * The commands and questions JavaScript sends the native side. A command with a promise resolves once what it moves
 * has settled, or rejects with a sentence that names the screen; a `void` one goes and reports its errors in the log.
 * The synchronous methods only read what the native side stored for them and answer at once.
 */
export interface Spec extends TurboModule {
  /** Sets the root of the app with `{ root, modals, overlays }`; resolves with the id of the root once all shows. */
  setRoot(commandId: string, layout: CodegenTypes.UnsafeObject): Promise<string>;
  /** Starts the hidden React root `apexnative.slots`, once; a second call does nothing. */
  startHost(): void;
  /** Replaces the default options of every layout, shown or not, and applies their look to what shows. */
  setDefaultOptions(options: CodegenTypes.UnsafeObject): void;
  /** Merges options into the layout with the id and applies them to what shows; an unknown id does nothing. */
  mergeOptions(componentId: string, options: CodegenTypes.UnsafeObject): void;
  /**
   * (Development) Takes the options at the paths (`topBar.subtitle`, a group's path for the whole group) out of the own
   * options of the layout with the id, so it shows what the layouts around it and the defaults give there, and applies
   * the result to what shows as `setDefaultOptions` does. An unknown id or path does nothing. Fast Refresh sends it
   * for the options an edit took out of a screen that shows.
   */
  clearOptions(componentId: string, paths: ReadonlyArray<string>): void;
  /** Pushes a layout over the screen in its stack; resolves with the id of the screen that shows (of a pair: its id). */
  push(commandId: string, componentId: string, layout: CodegenTypes.UnsafeObject): Promise<string>;
  /**
   * (Android, one React tree) Lays the screen of a coming push out in its stack before React renders it, and resolves
   * with its frame `{ width, height, padding }` in dp; null when it isn't prepared, and always on iOS.
   */
  preparePush(
    commandId: string,
    componentId: string,
    layout: CodegenTypes.UnsafeObject
  ): Promise<CodegenTypes.UnsafeObject | null>;
  /** Drops a screen that `preparePush` laid out; an unknown id does nothing. */
  cancelPreparedPush(componentId: string): void;
  /** Takes the screen and every screen above it off its stack; resolves with the id of the screen that went. */
  pop(commandId: string, componentId: string): Promise<string>;
  /** Takes the stack back to the screen with the id; resolves with that id. */
  popTo(commandId: string, componentId: string): Promise<string>;
  /** Takes the stack of the screen back to its first screen; resolves with the id of that screen. */
  popToRoot(commandId: string, componentId: string): Promise<string>;
  /** Replaces the screens of the screen's stack, the last one on top; resolves with the id of the new top screen. */
  setStackRoot(
    commandId: string,
    componentId: string,
    layout: ReadonlyArray<CodegenTypes.UnsafeObject>
  ): Promise<string>;
  /** (Android) Whether JavaScript has `BackHandler` listeners; iOS does nothing. */
  setBackListeners(active: boolean): void;
  /** Shows a layout as a modal over the top modal or the root; resolves with the id of the modal's root. */
  showModal(commandId: string, layout: CodegenTypes.UnsafeObject): Promise<string>;
  /** Dismisses the modal the screen is in; resolves with the id of the modal's root. */
  dismissModal(
    commandId: string,
    componentId: string,
    options?: CodegenTypes.UnsafeObject
  ): Promise<string>;
  /** Dismisses every modal; resolves with an empty string. */
  dismissAllModals(commandId: string, options?: CodegenTypes.UnsafeObject): Promise<string>;
  /** Shows a layout over everything, apart from the root and the modals; resolves with the id of its root. */
  showOverlay(commandId: string, layout: CodegenTypes.UnsafeObject): Promise<string>;
  /** Dismisses the overlay the screen is in; resolves with the id of the overlay. */
  dismissOverlay(commandId: string, componentId: string): Promise<string>;
  /** Dismisses every overlay; resolves with an empty string. */
  dismissAllOverlays(commandId: string): Promise<string>;
  /**
   * Shows the layout in a column of the screen's split view in place of what it showed; out of a split view and in the
   * compact width it is a push. Resolves with the id of the screen that shows.
   */
  showInColumn(
    commandId: string,
    componentId: string,
    column: string,
    layout: CodegenTypes.UnsafeObject
  ): Promise<string>;
  /**
   * Shows or hides a column of the screen's split view; resolves once the columns stopped moving, with nothing (on
   * Android `null`).
   */
  setColumnShown(
    commandId: string,
    componentId: string,
    column: string,
    shown: boolean
  ): Promise<void>;
  /** How a screen of a pair shows, `{ placement, layout, front, hidden }`; `{}` out of a pair. */
  getArrangementState(componentId: string): CodegenTypes.UnsafeObject;
  /** The safe area of the screen, `{ top, right, bottom, left }`; `{}` before its first layout. */
  getScreenInsets(componentId: string): CodegenTypes.UnsafeObject;
  /** The reserved regions of the screen, `{ division, occlusion }`; empty lists before its first layout. */
  getScreenRegions(componentId: string): CodegenTypes.UnsafeObject;
  /** Turns `ANN.HingeChanged` on and off. */
  setHingeUpdates(enabled: boolean): void;
  /** The last state of the device's hinge, `{ state, angle? }`; `{}` without a hinge. */
  getHinge(): CodegenTypes.UnsafeObject;
  /** The link that launched the app (Android: the current activity), given once; null without one. */
  getLaunchLink(): Promise<string | null>;
  /** What the native side tells at the start: `{ coldStart, splashLogo, arrangements }`. */
  getStartInfo(): CodegenTypes.UnsafeObject;
  /** New props of an open native screen: `{ props, removed, functions }`. */
  updateNativeScreenProps(componentId: string, update: CodegenTypes.UnsafeObject): void;
  /** The answer to `ANN.NativeScreenCall`: `{ value }` or `{ error }`. */
  resolveNativeScreenCall(callId: string, result: CodegenTypes.UnsafeObject): void;
  /** (iOS) Takes away a preview that waits, with its screen; a screen it opened stays. Android does nothing. */
  dismissPreview(previewId: string): void;
}

// Missing without the native side (Jest): every caller checks it is there.
export default TurboModuleRegistry.get<Spec>('ANNTurboModule') as Spec;
