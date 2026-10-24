import type { OptionsTopBarButton } from './buttons';

/** (iOS) A space in the buttons of the toolbar. It has no id, text, icon or component, so it is not a button. */
export interface OptionsToolbarSpace {
  /**
   * `'flexibleSpace'` takes the free width and pushes the next buttons to the far edge; `'fixedSpace'` is a gap of zero
   * width that starts a new capsule with the glass design (iOS 26+).
   */
  systemItem: 'flexibleSpace' | 'fixedSpace';
}

/**
 * (iOS 26+) The search field of the screen, placed between the buttons of the toolbar. The system puts it there for
 * any `topBar.searchBar.placement`, even `'stacked'`; it needs `topBar.searchBar.visible`. Before iOS 26 it is skipped
 * and the field is where the placement puts it.
 */
export interface OptionsToolbarSearchField {
  /** Marks the search field. */
  systemItem: 'searchField';
}

/**
 * (iOS) The toolbar at the bottom of a screen in a stack: the system toolbar of the stack, glass on iOS 26, with the
 * buttons of the shown screen. On iPhone with iOS 26 the floating tab bar takes the same place, so a screen with a
 * toolbar in tabs should hide the tab bar itself (`bottomTabs.visible: false`); the library doesn't link the two.
 * Android has no toolbar; it has the FAB.
 */
export interface OptionsToolbar {
  /**
   * Whether the toolbar shows. It applies when the screen appears and on `mergeOptions`, animated. A toolbar without
   * buttons is always hidden.
   * @default true
   */
  visible?: boolean;
  /**
   * The buttons from the leading edge, in the format of the bar buttons: text, icon, SF Symbol, `systemItem`, a
   * component (drawn before the screen shows), `enabled`, `color`, `style`, `menu` (it opens upward), `toggle`,
   * `showText`, `group`; also spaces and the search field. A press sends a button press. The color of a side doesn't
   * apply to them, and badges are not drawn. With the glass design (iOS 26+, see `UIDesignRequiresCompatibility`) each button has its own capsule, neighbor icons with the same
   * `group` share one (the toolbar has no real groups, so text buttons never share), and a zero-width space is put
   * between neighbors of different groups unless an explicit space is there. The list is replaced as a whole;
   * `mergeOptions` animates it.
   */
  buttons?: (OptionsTopBarButton | OptionsToolbarSpace | OptionsToolbarSearchField)[];
  /** The identifier tests find the toolbar by. */
  testID?: string;
}
