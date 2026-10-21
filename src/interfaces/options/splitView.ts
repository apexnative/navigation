/**
 * The width limits of a column of a split view, in points. Each is applied only when it is set; without it the
 * system chooses the size. The columns this type describes (middle, inspector) exist on iOS only.
 */
export interface OptionsSplitViewColumn {
  /** (iOS) The preferred width of the column. Without it, the width the system gives it. */
  width?: number;
  /** (iOS) The smallest width the column may take. Without it, the system's limit. */
  minWidth?: number;
  /** (iOS) The largest width the column may take. Without it, the system's limit. */
  maxWidth?: number;
}

/**
 * (iOS, iPadOS 26) The inspector column at the trailing edge: its width limits, and whether it shows. It does
 * nothing before iPadOS 26 or in a split view without an `inspector` column in its layout.
 */
export interface OptionsSplitViewInspector extends OptionsSplitViewColumn {
  /**
   * Whether the inspector shows. `true` shows the column and `false` hides it; without a value, nothing changes. It
   * applies when the split view is created (without animation), after its first appearance (the system shows the
   * inspector then by itself), and through `mergeOptions` (animated). Showing or hiding it reports a display mode
   * change with the inspector's state.
   */
  visible?: boolean;
}

/**
 * Which columns of a split view show. The three-column values need a `supplementary` column in the layout. Android
 * does not use it.
 */
export type SplitViewDisplayMode =
  | 'automatic'
  | 'secondaryOnly'
  | 'oneBesideSecondary'
  | 'oneOverSecondary'
  | 'twoBesideSecondary'
  | 'twoOverSecondary'
  | 'twoDisplaceSecondary';

/**
 * The columns of the split view the screen is in: which of them show, their widths, the button and the swipe that
 * reveal a hidden column, and the column shown on top when the split view collapses. On iOS all of it applies. On
 * Android only `width`, `presentsWithGesture`, `compactTopColumn` and `testID` apply, and through `mergeOptions` and
 * the default options as well as the layout.
 */
export interface OptionsSplitView {
  /**
   * (iOS) Which columns show. `'automatic'` leaves the choice to the system. What the user picks (the button, a swipe)
   * lasts until the next `mergeOptions`. It applies when the split view is created and through
   * `mergeOptions`, not when its screens appear. Every change reports a display mode change event. The earlier names
   * are read too: `'hidden'` is `'secondaryOnly'`, `'visible'` `'oneBesideSecondary'` (`'twoBesideSecondary'` with
   * three columns), `'overlay'` `'oneOverSecondary'` (`'twoOverSecondary'` with three columns); `'auto'` and any
   * other string are `'automatic'`.
   * @default 'automatic'
   */
  displayMode?: SplitViewDisplayMode;
  /**
   * (iOS) The side of the primary column: `'leading'` or `'trailing'`. Another value counts as `'leading'`. It applies
   * when the split view is created, whenever a screen inside appears, and through `mergeOptions`.
   * @default 'leading'
   */
  primaryEdge?: 'leading' | 'trailing';
  /**
   * The preferred width of the primary column. On iOS in points, applied only when set; without it, the system's
   * width. On Android in dp, 360 by default: the width of the primary pane, which together with the 360 dp of the
   * secondary one sets the width at which the panes stand side by side. It applies when the split view is created and
   * at once through `mergeOptions` or new default options.
   */
  width?: number;
  /** (iOS) The smallest width of the primary column, applied only when set. */
  minWidth?: number;
  /** (iOS) The largest width of the primary column, applied only when set. */
  maxWidth?: number;
  /**
   * (iOS) The look behind the primary column: `'sidebar'` gives it the sidebar background (noticeable in a Mac
   * Catalyst app) and `'none'` the plain one, also after `'sidebar'` was set.
   * @default 'none'
   */
  primaryBackgroundStyle?: 'sidebar' | 'none';
  /**
   * (iOS) The widths of the middle column. It does nothing when the layout has no `supplementary` column.
   */
  supplementary?: OptionsSplitViewColumn;
  /** (iOS, iPadOS 26) The inspector column at the trailing edge: its widths and whether it shows. */
  inspector?: OptionsSplitViewInspector;
  /**
   * (iOS) How the columns share the width: `'tile'` puts them side by side, `'overlay'` lays the primary column over
   * the secondary one, `'displace'` pushes the secondary column aside, and `'automatic'` leaves it to the system.
   * @default 'automatic'
   */
  splitBehavior?: 'automatic' | 'tile' | 'overlay' | 'displace';
  /**
   * (iOS) Whether the button that shows and hides the columns appears: `'always'`, `'never'`, or `'automatic'`, which
   * hides it when `presentsWithGesture` is `false`.
   * @default 'automatic'
   */
  displayModeButton?: 'automatic' | 'never' | 'always';
  /**
   * Whether a swipe from the edge reveals a hidden column. On Android the swipe moves the secondary pane over the
   * primary one and back, when they don't fit side by side; it applies when the split view is created and at once
   * through `mergeOptions` or new default options.
   * @default true
   */
  presentsWithGesture?: boolean;
  /**
   * (iOS) Whether a three-column split view has a button that leaves only the secondary column.
   * @default false
   */
  showsSecondaryOnlyButton?: boolean;
  /**
   * The column shown on top when the split view collapses (iPhone, a narrow window, a narrow Android screen) and the
   * layout has no `compact` content: `'primary'` or `'secondary'`. Without it, the system chooses on iOS (read when it
   * collapses, from the final options with the defaults) and `'primary'` is on top on Android. On Android `'secondary'`
   * opens the secondary pane without animation on the first narrow layout, and a new value applies at the next
   * collapse.
   */
  compactTopColumn?: 'primary' | 'secondary';
  /**
   * An identifier for tests and accessibility tools. On Android the panes get `<testID>.primary` and
   * `<testID>.secondary`. The back arrow of the first screen of a sliding pane takes the `topBar.backButton.testID` of
   * that screen. Without it, the elements have no id. It changes with `mergeOptions`. On iOS the button that shows
   * the columns gets no id: the system draws it in a column-style split view and gives no way to name it; find it by
   * its accessibility label.
   */
  testID?: string;
}
