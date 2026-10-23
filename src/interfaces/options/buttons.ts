import type { Color, FontFamily, FontStyle, FontWeight, ImageResource } from './common';

/**
 * The system button a button shows. On iOS it is the system item of the same name, with the text of the user's
 * language; a name the system doesn't know shows `'done'`. On Android only `'close'` is read: it draws the library's
 * close icon when the button has no `icon` of its own, and every other name is ignored.
 */
export type SystemItemIcon =
  | 'done'
  | 'cancel'
  | 'edit'
  | 'save'
  | 'add'
  | 'flexibleSpace'
  | 'fixedSpace'
  | 'compose'
  | 'reply'
  | 'action'
  | 'organize'
  | 'bookmarks'
  | 'search'
  | 'refresh'
  | 'stop'
  | 'camera'
  | 'trash'
  | 'play'
  | 'pause'
  | 'rewind'
  | 'fastForward'
  | 'undo'
  | 'redo'
  | 'close';

/** (iOS) The space around the image of an icon, in points. */
export interface IconInsets {
  /**
   * The space above the image, in points.
   * @default 0
   */
  top?: number;
  /**
   * The space left of the image, in points.
   * @default 0
   */
  left?: number;
  /**
   * The space below the image, in points.
   * @default 0
   */
  bottom?: number;
  /**
   * The space right of the image, in points.
   * @default 0
   */
  right?: number;
}

/**
 * A filled shape behind the icon of a button or of the back button. It takes effect only when `color` is set; without
 * a color the other fields are ignored.
 *
 * On iOS the icon is drawn onto the shape and the button becomes a button of its own (the back button keeps its
 * history menu), without the glass capsule on iOS 26 by default. On Android the icon sits on the shape, mirrored in a
 * right-to-left layout. Layers merge differently: on iOS an object merges field by field like any other, on Android
 * an `iconBackground` that has a `color` replaces the earlier one as a whole.
 */
export interface IconBackgroundOptions {
  /** The color of the shape. Without it the whole `iconBackground` has no effect. */
  color?: Color;
  /**
   * The color of the shape while the button is disabled. Without it the shape keeps `color` on iOS and disappears on
   * Android. The back button is never disabled, so it ignores this field.
   */
  disabledColor?: Color;
  /**
   * The radius of the corners of the shape, in points (iOS) or dp (Android).
   * @default 0
   */
  cornerRadius?: number;
  /** The width of the shape, in points or dp. By default the width of the icon; on Android never less than that. */
  width?: number;
  /** The height of the shape, in points or dp. By default the height of the icon; on Android never less than that. */
  height?: number;
}

/**
 * An item of a menu. Picking it sends a button press with its `id` to the screen, and the screen runs the `onPress`
 * of the item that has this id. Item ids and button ids of one screen must not repeat.
 */
export interface OptionsTopBarButtonMenuItem {
  /**
   * The id of the item, sent as `buttonId` when the item is picked. An item without an id sends nothing on iOS.
   */
  id?: string;
  /** The text of the item. Without it the item shows an empty string. */
  title?: string;
  /**
   * The icon in front of the text. On iOS an SF Symbol or an image; on Android a drawable resource or an image
   * source. On Android the icons show in the menu as soon as one item has one, tinted like the menu's other icons (the
   * error color for a destructive item). An icon that fails to load leaves the item without one.
   */
  icon?: ImageResource;
  /** Whether the item is drawn as a destructive action: red on both platforms. */
  destructive?: boolean;
  /** Whether the item is shown but can't be picked. */
  disabled?: boolean;
  /**
   * Whether the item carries a check mark. On Android the item is checkable, also outside a `singleSelection` menu.
   * With `singleSelection` on the menu, picking an item moves the mark to it, and `mergeOptions` with `checked`
   * moves it from code.
   */
  checked?: boolean;
  /**
   * The identifier tests find the item by: the accessibility identifier on iOS, the resource id and view tag of the
   * item on Android. Where the system gives no way to set it, the item is found by its text.
   */
  testID?: string;
}

/** A submenu: an item that opens a menu of its own, of any depth. Submenus carry no check mark. */
export interface OptionsTopBarButtonSubmenu {
  /** The text of the submenu item. Without it the item shows an empty string. */
  title?: string;
  /** The icon of the submenu item, as for a menu item. */
  icon?: ImageResource;
  /** Whether only one item of this submenu can be checked at a time. */
  singleSelection?: boolean;
  /** The items and submenus, in order. */
  items: OptionsTopBarButtonMenuElement[];
  /** The identifier tests find the submenu item by, as for a menu item. */
  testID?: string;
}

/** An item or a submenu of a menu. An element with `items` is a submenu. */
export type OptionsTopBarButtonMenuElement =
  OptionsTopBarButtonMenuItem | OptionsTopBarButtonSubmenu;

/**
 * A menu of the top bar: the menu of a button, the overflow menu, the menu of the title. A list of `items` sent with
 * `mergeOptions` replaces the earlier list as a whole, and `items: []` removes the menu; `title` and
 * `singleSelection` merge field by field (iOS). What the user picks in a `singleSelection` menu stays with the screen,
 * through it showing again, a `mergeOptions` that sends the same items again and `setDefaultOptions`; a `mergeOptions`
 * into the screen that changes the checks of that menu's level, or takes the item away, replaces it. A merge into the
 * stack or the tabs around the screen doesn't.
 */
export interface OptionsTopBarButtonMenu {
  /** The title above the items. (iOS) Android doesn't show it. */
  title?: string;
  /**
   * Whether only one item of the top level can be checked at a time: picking an item moves the check mark to it. On
   * iOS this is the system's single-selection menu; on Android the items are checkable.
   */
  singleSelection?: boolean;
  /** The items and submenus, in order. An empty list removes the menu. */
  items?: OptionsTopBarButtonMenuElement[];
}

/**
 * A button of the top bar (`leftButtons`, `rightButtons`, `centerButtons`) or of the toolbar (`toolbar.buttons`). The
 * kinds are picked by the fields that are set. On iOS, the first that fits wins: a `component`, then an icon with text
 * and `showText`, then an icon with `iconBackground` or `iconInsets`, then an SF Symbol, an image `icon`, a `text`,
 * a `systemItem`; a button with none of them is not shown. On Android a button is a menu item of the bar: text, icon
 * or a component. Lists of buttons are replaced as a whole and take nothing from the lists of lower layers.
 *
 * Text, icon, color and similar fields are the same in all four places; where a place or a side differs, the field
 * says so.
 */
export interface OptionsTopBarButton {
  /**
   * The id of the button: the `buttonId` of its press, the key of the press handler, of a toggle's state and of a
   * menu's choice, and the source of a popover (`modal.popover.sourceButtonId`). Required: a button without an id is
   * not shown on either platform, the error goes to the log, and in development to a red box.
   */
  id: string;
  /** (Android) Whether the text is drawn in capitals. Without it, as the theme says (not in capitals in Material 3). */
  allCaps?: boolean;
  /**
   * The icon: an image, or an icon for each platform (`{ ios: 'SF Symbol', android: 'resource' }`). A platform
   * without its side shows no icon.
   *
   * On iOS an SF Symbol without a `color` is drawn as a template in the color of the bar and fades when disabled
   * (`disableIconTint` keeps its own colors); an image is drawn as it is, tinted when a color is set. With a `text`,
   * the text becomes the button's label: the bar shows only the icon, the overflow menu and the customization panel
   * show the text. An image with `iconBackground` or `iconInsets` makes a button of its own.
   *
   * On Android the icon is a drawable resource of the app or an image source, tinted with `color` (`disabledColor`
   * when disabled), or as it is without one. An icon that fails to load leaves the button without it, with its `text`
   * if it has one, and reports the error to the log (in development, a red box).
   */
  icon?: ImageResource;
  /**
   * (iOS) The space around the image of `icon`; the button becomes a button of its own, without a capsule on iOS 26.
   */
  iconInsets?: IconInsets;
  /**
   * A React component as the button. The component handles its own touches: the bar sends no press for it, and
   * `menu`, `toggle` and `style` have no effect. It is drawn before the screen shows.
   */
  component?: OptionsTopBarButtonComponent;
  /**
   * The system button. Only `'close'` works on Android. On the toolbar, spaces and the search field have objects of
   * their own (`OptionsToolbarSpace`, `OptionsToolbarSearchField`).
   */
  systemItem?: SystemItemIcon;
  /**
   * The text of the button. A button with an icon keeps it as a label for the overflow menu, the customization panel
   * and VoiceOver (iOS) or as the text of the menu item (Android, where it also shows when there is room).
   */
  text?: string;
  /** The text a screen reader says for the button. */
  accessibilityLabel?: string;
  /**
   * The font family of the text. On iOS the font is built from all font options together, the size defaulting to 17.
   */
  fontFamily?: FontFamily;
  /** The slant of the text. */
  fontStyle?: FontStyle;
  /** The weight of the text. */
  fontWeight?: FontWeight;
  /** The size of the text: points on iOS (17 once any font option is set), sp on Android. */
  fontSize?: number;
  /**
   * Whether the button reacts. A disabled button is dimmed (see `disabledColor`) and a press does nothing.
   * @default true
   */
  enabled?: boolean;
  /**
   * Whether the icon keeps its own colors instead of being tinted. On iOS an SF Symbol stays multicolor and a side
   * color from `mergeOptions` doesn't repaint it; on Android the icon gets no tint.
   */
  disableIconTint?: boolean;
  /**
   * The color of the text and of the icon; it beats the color of the side (`leftButtonColor` and the like). On iOS a
   * `'prominent'` button uses it only as the tint of its capsule.
   */
  color?: Color;
  /** The color while the button is disabled; it beats the color of the side. Android defaults to light gray. */
  disabledColor?: Color;
  /**
   * A shape behind the icon. It works with an `icon` image and a `color` of the shape; on iOS the button becomes a
   * button of its own without menu, toggle or style, and without a capsule on iOS 26 by default. The color of a whole
   * side is `leftButtonBackgroundColor` / `rightButtonBackgroundColor`.
   */
  iconBackground?: IconBackgroundOptions;
  /**
   * (iOS) `'plain'` or `'prominent'`, the main action of the bar (Done, Send). On iOS 26 a prominent button is a
   * capsule in the button's tint with bold text or icon on it (the tint is `color`, the bar's tint without it);
   * before iOS 26 it is the system's done style with bold text. Not for component buttons, buttons with
   * `iconBackground`, and `showText` buttons.
   * @default 'plain'
   */
  style?: 'plain' | 'prominent';
  /**
   * The identifier tests find the button by: the accessibility identifier on iOS, the resource id and the view tag
   * on Android (in the bar and in the overflow menu). A component button puts it on its slot; the component sets its
   * own inside.
   */
  testID?: string;
  /**
   * (iOS 26+) Whether the button has no glass capsule under it; neighbors keep theirs. By default `true` for
   * component buttons and for icon buttons with a background or insets, `false` for the rest. In a `group` of several
   * buttons the capsule is shared and this has no effect. A change by `mergeOptions` builds the buttons anew.
   */
  hideSharedBackground?: boolean;
  /**
   * (iOS) Whether a button with an `icon` and a `text` shows both side by side, as one button on the bar's glass. The
   * button is a view of its own; `color`, the side color, fonts, `enabled`, `badge`, `group`, `testID` and
   * `accessibilityLabel` work, while `menu`, `toggle`, `style` and `iconBackground` don't. In the overflow menu it
   * is an item with text and icon. For the bar and the toolbar.
   */
  showText?: boolean;
  /**
   * (iOS 26+) Buttons next to each other with the same group share one capsule; a button without a group has its
   * own. Before iOS 26 it does nothing, on iOS 15 there are no groups. A side that has a group of several buttons
   * doesn't animate button changes. The toolbar has no groups: only icons share a capsule there. For center buttons
   * the group is a section of the overflow menu, and its name is the id the customization is saved under.
   */
  group?: string;
  /**
   * (iOS 26+) A badge on the button: a number, a short text, or `true` for a dot. `0`, `''`, `false` and `null` show
   * none. `mergeOptions` changes it on the fly. The toolbar doesn't draw badges.
   */
  badge?: number | string | boolean | null;
  /** (iOS 26+) The color of the badge. Without it, the system red. */
  badgeColor?: Color;
  /** (iOS 26+) The color of the number or text of the badge. Without it, white. */
  badgeTextColor?: Color;
  /**
   * (iOS) Whether the button is a toggle: every press flips `selected` and sends a press with the new state. The state
   * stays with the screen under the button's id, through it showing again, a `mergeOptions` that sends the same
   * buttons again and `setDefaultOptions`; a `mergeOptions` into the screen that changes `selected` replaces it (a merge
   * into the stack around it doesn't). A button set again
   * without `selected` takes the state of the button with the same id. Not for component buttons,
   * buttons with `iconBackground`, and `showText` buttons.
   */
  toggle?: boolean;
  /**
   * (iOS) Whether the button is drawn as switched on: on iOS 26 filled with its color, with the icon or text on it.
   * For a toggle this is the starting state, and `mergeOptions` flips it from code. Works without `toggle` too.
   * @default false
   */
  selected?: boolean;
  /**
   * The menu of the button. It opens on a press, or on a long press with `menuOnLongPress`. A pick sends a press with
   * the id of the item. On iOS it is the system menu (on the toolbar it opens upward), not for component buttons and
   * buttons with `iconBackground`; on Android a popup under the button without a title (from the end of the bar for a
   * button in the overflow), not for component buttons.
   */
  menu?: OptionsTopBarButtonMenu;
  /**
   * Whether a press does the button's own action (a press event, a toggle) and the menu opens on a long press.
   * Without it the menu opens on a press.
   * @default false
   */
  menuOnLongPress?: boolean;
  /**
   * (iOS 16+) For a center button, when `topBar.customizationId` is set: `'fixed'` stays in place, `'movable'` moves
   * but can't be removed, `'optional'` moves and can be removed and added back in the customization panel. A group
   * moves as a whole. Renaming a group or a button resets the saved customization.
   * @default 'movable'
   */
  customization?: 'fixed' | 'movable' | 'optional';
  /**
   * (iOS 16+) Whether an `'optional'` center button is in the bar before the user customizes it. With `false` the user
   * adds it from the customization panel. On iPhone center buttons are in the overflow menu anyway.
   * @default true
   */
  shownByDefault?: boolean;
  /**
   * Whether a right button stays out of the overflow menu. On iOS 16+ it goes to the pinned group at the trailing
   * edge, behind the overflow button, in a capsule of its own (several pinned buttons share it); when space runs
   * out iOS hides the title first. On Android it stays in the bar while other right buttons move to the overflow when
   * they don't fit. No effect on left and center buttons.
   * @default false
   */
  pinned?: boolean;
  /**
   * Which buttons move to the overflow menu last: `'high'` stays longest, `'low'` goes first. On iOS 27+ it is the
   * visibility priority of the button (it matters in the narrow vertical bar of iPhone Duo too); before iOS 27 it
   * does nothing. On Android the bar keeps the right buttons of the highest priority, ties in list order.
   * @default 'standard'
   */
  priority?: 'high' | 'standard' | 'low';
  /**
   * (iOS 27.1+) Whether the button may move to the vertical bar of iPhone Duo: `true` allows it, a component button
   * too, narrowed to the width of the bar; `false` keeps it in the horizontal row. Without it the system decides
   * and a component button stays in the row.
   */
  vertical?: boolean;
}

/**
 * The React component of a bar button. The props stay in JavaScript under the id of the component; with the same
 * `name` and `id` a `mergeOptions` with new `passProps` updates the mounted component, a different `id` or `name`
 * replaces it.
 */
export interface OptionsTopBarButtonComponent {
  /**
   * The id of the component. Without it, the library gives one made of the screen, the component's place in the
   * options and its `name`: it stays the same for every `mergeOptions` of the screen, so the mounted component stays
   * and only its props change. The same `name` and `id` mean the same component.
   */
  id?: string;
  /** The name of the registered component. */
  name: string;
  /** The props of the component. They stay in JavaScript; the native side never sees them. */
  passProps?: object;
  /** (Android) The width of the component, in dp. The cell around it is wider by its margins. */
  width?: number;
  /** (Android) The height of the component, in dp. */
  height?: number;
}
