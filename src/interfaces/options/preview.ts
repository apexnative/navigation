/**
 * An entry of the menu under a preview. With `actions` it opens a submenu instead of reporting a press.
 */
export interface PreviewMenuItem {
  /** The identifier a press reports to the screen of the preview. */
  id: string;
  /** The text of the entry. */
  title: string;
  /** How the entry looks: `'destructive'` in the red of a destructive action, `'selected'` with a check mark. */
  style?: 'default' | 'selected' | 'destructive';
  /** The entries of a submenu. */
  actions?: PreviewMenuItem[];
}

/**
 * (iOS) The preview of a screen that is pushed, shown by a long press on a view of the screen it is pushed from: the
 * system's context menu with the small screen and a menu under it. A tap on the preview opens the screen. Set by
 * `usePreview` through the push of the preview; apps don't set it. Android has no previews.
 * @internal
 */
export interface PreviewOptions {
  /**
   * The `nativeID` of the view of the source screen that shows the preview. The native side looks for that view
   * right away and for up to 30 frames after, since a view just rendered mounts a little after the command. A preview
   * whose view isn't found goes away at once, and its push is rejected.
   */
  nativeID: string;
  /**
   * Whether a tap on the preview opens the screen. The tap is reported with a preview-completed event; with `true` the
   * screen is also shown: the stack goes back to the screen with the same key when the source is in a stack, else the
   * screen is pushed without animation.
   */
  commit?: boolean;
  /** The menu under the preview. */
  actions?: PreviewMenuItem[];
  /** The width of the preview. Without it, the width of the source screen. */
  width?: number;
  /** The height of the preview. Without it, the height of the source screen. */
  height?: number;
}
