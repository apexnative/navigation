import type {
  IconBackgroundOptions,
  OptionsTopBarButton,
  OptionsTopBarButtonMenu,
} from './buttons';
import type { Color, FontFamily, FontStyle, FontWeight, ImageResource } from './common';

/**
 * The React component that is the title of a screen. The props stay in JavaScript under the id of the component; with
 * the same `name` and `id`, a `mergeOptions` with new `passProps` updates the mounted component, a different `id` or
 * `name` replaces it.
 */
export interface OptionsTopBarTitleComponent {
  /** The id of the component. Without it, the library gives one. The same `name` and `id` mean the same component. */
  id?: string;
  /** The name of the registered component. */
  name: string;
  /**
   * How the component sits in the bar: `'center'` gives it the size of its content in the middle of the bar, `'fill'`
   * gives it the whole room between the side buttons, from the back button (or the leading edge) to the right
   * buttons. On Android `'fill'` leaves 16 dp to the buttons on each side.
   * @default 'center'
   */
  alignment?: 'center' | 'fill';
  /** The props of the component. They stay in JavaScript; the native side never sees them. */
  passProps?: object;
  /**
   * Whether showing the screen waits for the component to draw its content. On iOS the wait is at most 1 s and
   * `false` shows the screen at once, the title filling in later. On Android a push waits for bar components only
   * with `animations.push.waitForRender: true`, at most 0.5 s, and a title with `false` is not waited for.
   * @default true
   */
  waitForRender?: boolean;
}

/** The file whose card heads the menu of the title. */
export interface OptionsTopBarTitleDocument {
  /**
   * The URL of the file, with its scheme, or a path where `~` is the home folder of the app. An empty string removes
   * the card.
   */
  url: string;
}

/** The title of a screen: text and its font, or a React component, with the menu, rename and document of iOS. */
export interface OptionsTopBarTitle {
  /**
   * The text of the title. Setting it removes a React title. On iOS it is the title of the screen's navigation item,
   * the label the system shows on the next screen's back button before iOS 26, and what Rename edits. On Android it
   * is one line cut with an ellipsis, and also the text of the large title; a title component set together with the
   * text drops the text and the subtitle (with a warning in development).
   */
  text?: string;
  /**
   * The size of the font. On iOS the font is built only when some font option is set, on top of the system font of
   * the place; without any, the system picks (and shrinks the title beside a subtitle). On Android it is in sp,
   * following the system font size, 22 by default.
   */
  fontSize?: number;
  /** The color of the text. Without it the system color on iOS and the theme's on-surface color on Android. */
  color?: Color;
  /** The font family of the title, built the same way as `fontSize`. */
  fontFamily?: FontFamily;
  /** The slant of the title. */
  fontStyle?: FontStyle;
  /** The weight of the title; the nearest face is used. */
  fontWeight?: FontWeight;
  /**
   * A React component in place of the text. Text and component exclude each other: the one set last wins, also in
   * `mergeOptions`. On iOS it is the navigation item's title view, it hides the subtitle, and the title menu works with
   * it; on Android it sits between the button groups, centered vertically, and fades with the row under a large title.
   */
  component?: OptionsTopBarTitleComponent;
  /**
   * (iOS 16+) A menu that opens on a tap on the title, which shows a chevron next to it. The Rename item comes first
   * when `rename` is set, then the items. The large title has no chevron and opens nothing. Android has no title menu:
   * use `topBar.overflowMenu`.
   */
  menu?: OptionsTopBarButtonMenu;
  /**
   * (iOS 16+) Whether the title menu has a Rename item. It turns the title into a field in the bar with the text
   * selected. When editing ends, an empty text restores the old title, an unchanged one does nothing, a new one is
   * written to `text` and sent as a title-renamed event. The other items the system suggests (Duplicate, Move,
   * Export, Print) are not shown.
   */
  rename?: boolean;
  /**
   * (iOS 16+) A file whose card (icon, name, type, size) opens the title menu, even with the card alone. For a file
   * that doesn't exist the card shows no size.
   */
  document?: OptionsTopBarTitleDocument;
  /**
   * (iOS 26+) Puts the title on a Liquid Glass capsule like the buttons: `'regular'` or `'clear'`; `'none'` and any
   * other value mean no capsule. A text title and the subtitle are two lines on the capsule; a component title sits on
   * it as it is, the capsule taking its size. Before iOS 26 and on Android it does nothing.
   */
  glass?: 'regular' | 'clear' | 'none';
  /** (Android) The height of the row of title and buttons, in dp. By default 64, regardless of `topBar.height`. */
  height?: number;
  /**
   * (Android) The distance from the top of the bar to the row of title and buttons, in dp; the whole row moves, with
   * the back button and the buttons.
   * @default 0
   */
  topMargin?: number;
  /**
   * (Android) How a text title sits: `'center'` in the middle of the bar (shifted when it would touch the buttons),
   * `'fill'` across the whole room between the buttons from the start; without it from the start, 16 dp from the left
   * buttons, as in Material. A component has its own `component.alignment`. iOS places the title by `topBar.style`.
   */
  alignment?: 'center' | 'fill';
}

/** The subtitle: a second line under the title. */
export interface OptionsTopBarSubtitle {
  /**
   * The text of the second line. An empty text removes it. On iOS 26 it is the system subtitle, under the title, the
   * large title and the collapsed bar, and on the capsule when the title has `glass`; a React title hides it. Before
   * iOS 26 it makes a two-line title view, only without a large title or a React title. On Android it is the line under
   * the title, hidden when empty and dropped when the title is a component.
   */
  text?: string;
  /**
   * The size of the font. On iOS 26 only what is set goes to the system subtitle, and any font option sets the whole
   * font; before iOS 26 the size is 12. On Android it is in sp, 14 by default.
   */
  fontSize?: number;
  /** The color of the subtitle. On Android the theme's on-surface-variant color by default. */
  color?: Color;
  /** The font family, handled like `fontSize`. */
  fontFamily?: FontFamily;
  /** The slant of the font, handled like `fontSize`. */
  fontStyle?: FontStyle;
  /** The weight of the font, handled like `fontSize`. */
  fontWeight?: FontWeight;
  /**
   * (Android) `'center'` puts the subtitle in the middle of the title row; without it, at the start. iOS places the
   * subtitle by the title.
   */
  alignment?: 'center';
}

/**
 * The back button of the screen. Every screen of a stack but the first has one; a screen sets its own with its own
 * options. On iOS it is the system button, whose label and mode UIKit reads from the screen below, while color and font
 * come from the bar's look of the shown screen; an own button replaces it only with `enableMenu: false` or an
 * `iconBackground`. On Android it is the navigation button of the left bar.
 */
export interface OptionsTopBarBackButton {
  /**
   * (Android) The description of the button for screen readers.
   * @default 'Navigate Up'
   */
  accessibilityLabel?: string;
  /**
   * The id sent as `buttonId` of the press that the back button, the swipe back and the history menu send instead of
   * leaving when the screen holds the user back (`usePreventLeave`).
   * @default 'ApexNavigation.back'
   */
  id?: string;
  /**
   * The arrow: an image, or an icon for each platform. On iOS it is the back indicator image of the bar, tinted when
   * `color` is set; an SF Symbol is used as a symbol; with `iconBackground` or `enableMenu: false` it is the icon of an
   * own button. On Android it replaces the arrow of the navigation button and is tinted with `color` (the theme's
   * color without it); with `iconBackground` it sits on the shape.
   */
  icon?: ImageResource;
  /**
   * Whether the back button shows. On iOS it hides the button of the navigation item, and the bar counts it as hidden
   * when deciding whether an inline large title fits. On Android `false` removes the arrow and `true` on the first
   * screen shows one. It only hides the button; holding the user back is `usePreventLeave`.
   * @default true
   */
  visible?: boolean;
  /**
   * (iOS) The text of the back button. On iOS 26 the system button without it is an arrow alone, since the title of
   * the screen below is never shown; before iOS 26 it is that title while it fits. It is set on the screen below, as
   * its back button title.
   */
  title?: string;
  /**
   * (iOS) `true` shows the title of the screen below (or `title`), `false` shows an arrow alone (the minimal mode).
   * Without it the system decides; on iOS 26 that is the arrow.
   */
  showTitle?: boolean;
  /**
   * The color of the arrow and of the text. Without it the system color on iOS and the theme's on-surface color on
   * Android.
   */
  color?: Color;
  /**
   * (iOS) The size of the font of the text. The font is built when any font option is set, the size defaulting to
   * 17. The system rebuilds the button to show a new font, so the library flips its mode back and forth, not during a
   * transition. Android has no text on the arrow.
   */
  fontSize?: number;
  /** (iOS) The font family of the text, handled like `fontSize`. */
  fontFamily?: FontFamily;
  /** (iOS) The slant of the font of the text, handled like `fontSize`. */
  fontStyle?: FontStyle;
  /** (iOS) The weight of the font of the text, handled like `fontSize`. */
  fontWeight?: FontWeight;
  /**
   * A shape behind the icon, as for a button. It takes effect with a `color` of the shape. On iOS the icon (the
   * chevron by default) is drawn on it and the back button becomes an own button that keeps the history menu unless
   * `enableMenu` is `false`; without a capsule on iOS 26 by default. On Android the arrow sits on the shape.
   */
  iconBackground?: IconBackgroundOptions;
  /**
   * (Android) The identifier tests find the button by: the view tag and the resource id. Without it, `BackButton`.
   * On iOS it can't be set: UIKit names the system back button `BackButton` itself, and the library leaves that
   * identifier alone; it applies there to an own back button only (`enableMenu: false` or `iconBackground`).
   */
  testID?: string;
  /**
   * (iOS) Whether a long press opens the history menu. `false` makes the screen use an own button without the menu.
   * @default true
   */
  enableMenu?: boolean;
  /**
   * (iOS) How the system button shortens its text when the bar is full: `'default'` uses the title, then "Back", then
   * the arrow; `'generic'` "Back", then the arrow; `'minimal'` the arrow alone. `showTitle: false` means `'minimal'`.
   * Color, font and menu stay.
   */
  displayMode?: 'default' | 'generic' | 'minimal';
  /**
   * (iOS 26+) Whether the Liquid Glass capsule under the back button is hidden. It works only for an own button with
   * an `iconBackground`, where the shape is already drawn into the icon; the system button ignores it.
   * @default true
   */
  hideSharedBackground?: boolean;
  /**
   * Whether the back button, the swipe back and the history menu take the screen off the stack. With `false` they
   * send a button press with the back button's `id` instead. Set by `usePreventLeave`; apps don't set it.
   * @default true
   * @internal
   */
  popStackOnPress?: boolean;
}

/** The background of the bar: a color, a component, translucency and blur. */
export interface OptionsTopBarBackground {
  /**
   * The color of the bar. On iOS it fills both the standard look and the one at the scroll edge; on iOS 26 the bar is
   * then solid in this color instead of glass. A transparent color makes a transparent bar. It beats `blur` and
   * `translucent`. With a large title on iOS 26 the color is drawn into the list under the title, and the collapsed bar
   * paints itself. On Android it is the background of the bar with the status bar strip, by default the theme's
   * surface color, changing at once on push and pop. Together with `component` the color lies under the component.
   */
  color?: Color;
  /**
   * (iOS) Whether the background component is clipped to the bar, so that it doesn't reach under the status bar, in the
   * bar and in the list under the large title.
   * @default false
   */
  clipToBounds?: boolean;
  /**
   * A React component as the whole background of the bar, also under the status bar. On iOS it sits in the bar's own
   * background view, above the system background and below the title and buttons, stretching with a large title, and it
   * cross-fades with the component of the previous screen during push and pop. On Android it is a view under the row,
   * the height of the bar; one at a time, and a screen without a component has none.
   */
  component?: OptionsTopBarBackgroundComponent;
  /**
   * (iOS) Whether content shows through the bar. By default `true` on iOS 26 (glass) and `false` before. With `true`
   * the bar is glass on iOS 26 and the system translucent bar before; with `false` it is solid in the system color.
   * `color` and `blur` beat it. Only an explicit `true` puts the screen under the bar.
   */
  translucent?: boolean;
  /**
   * (iOS) Whether the background is a system chrome blur on a transparent base, a matte effect simpler than glass. It
   * beats `translucent`; `color` beats it. `false` returns the usual bar.
   */
  blur?: boolean;
}

/** The React component that is the background of the bar. */
export interface OptionsTopBarBackgroundComponent {
  /** The id of the component. Without it, the library gives one. The same `name` and `id` mean the same component. */
  id?: string;
  /** The name of the registered component. */
  name: string;
  /** The props of the component. They stay in JavaScript; the native side never sees them. */
  passProps?: object;
  /**
   * Whether a push waits for the component to draw, at most 1 s on iOS.
   * @default true
   */
  waitForRender?: boolean;
}

/** (iOS) The font and color of the title while the list is at its top edge. */
export interface OptionsTopBarScrollEdgeAppearanceTitle {
  /** The size of the font, handled like `topBar.title.fontSize`. */
  fontSize?: number;
  /** The color of the title at the edge. On iOS 26 it is fixed during a push, like the usual title color. */
  color?: Color;
  /** The font family, handled like `topBar.title.fontFamily`. */
  fontFamily?: FontFamily;
  /** The slant, handled like `topBar.title.fontStyle`. */
  fontStyle?: FontStyle;
  /** The weight, handled like `topBar.title.fontWeight`. */
  fontWeight?: FontWeight;
}

/** (iOS) The background of the bar while the list is at its top edge. */
export interface OptionsTopBarScrollEdgeAppearanceBackground {
  /**
   * The color of the bar at the edge, when `active` is set. Without it, `background.color`; a transparent color makes a
   * transparent edge.
   */
  color?: Color;
  /**
   * Whether content shows through the bar at the edge, when `active` is set. By default the same as
   * `background.translucent`.
   */
  translucent?: boolean;
}

/**
 * (iOS) How the bar looks while the list is at its top edge. Without `active` the edge looks as the usual bar, except
 * for the transparency under a large title on iOS 26; the title and the border at the edge still come from the fields
 * here or from the usual ones. Android doesn't read it.
 */
export interface OptionsTopBarScrollEdgeAppearance {
  /** The font and color of the title at the edge. Without it, `topBar.title`. Works without `active`. */
  title?: OptionsTopBarScrollEdgeAppearanceTitle;
  /** The background at the edge, used when `active` is set. */
  background?: OptionsTopBarScrollEdgeAppearanceBackground;
  /**
   * Whether the bar has a background of its own at the edge (`background`). With it the edge isn't made transparent
   * under a large title, and on iOS 26 an opaque color covers the large title. A change by `mergeOptions` applies at
   * once.
   * @default false
   */
  active?: boolean;
  /**
   * Whether there is no line under the bar at the edge. Works without `active`.
   * @default false
   */
  noBorder?: boolean;
  /**
   * The color of the line at the edge. Without it, the system shadow color. A change by `mergeOptions` applies at once.
   */
  borderColor?: Color;
}

/** The large title: iOS's system one, and Material 3's large top app bar on Android. */
export interface OptionsTopBarLargeTitle {
  /**
   * Whether the large title shows: `true` is `'always'`, `false` is `'never'`. `displayMode` beats it. Without either,
   * `'never'`. A screen with a large title goes under the bar.
   */
  visible?: boolean;
  /**
   * When the large title shows; it beats `visible`. `'always'` on this screen; `'never'` not; `'automatic'` as on the
   * screen below, and the first screen of a stack shows it. `'inline'` (iOS 17+) puts it in the bar row instead of
   * under it, and only on a screen without a back button (the first, or `backButton.visible: false`); next to a back
   * button or a left button the system shows it as `'always'`, and on the first screen the left buttons move to the
   * overflow; before iOS 17 it is `'always'`. The buttons of such a screen must be in its own options. On Android
   * `'inline'` means `'never'`.
   */
  displayMode?: 'always' | 'never' | 'automatic' | 'inline';
  /**
   * Whether the large title collapses into the bar as the list scrolls. With `false` on iOS the bar follows an empty
   * scroll view instead of the list, so the title stays as at the top of the list; under the row it then needs a
   * background (`background.color`, else a system blur), and `searchBar.hideOnScroll` doesn't work. On Android `false`
   * leaves the bar as it is and stops listening to the list. On iOS 26 in a right-to-left layout nothing collapses.
   * @default true
   */
  collapseOnScroll?: boolean;
  /**
   * The size of the font. On iOS the font is built only when some font option is set, on top of the system font
   * (34 pt); on Android it is in sp, 28 by default.
   */
  fontSize?: number;
  /**
   * The color of the large title, in both looks of the bar on iOS. On Android the theme's on-surface color by default.
   */
  color?: Color;
  /** The font family, handled like `fontSize`. */
  fontFamily?: FontFamily;
  /** The slant, handled like `fontSize`. */
  fontStyle?: FontStyle;
  /** The weight, handled like `fontSize`. */
  fontWeight?: FontWeight;
}

/** The scope (filter) of the search: a segment under the field on iOS, a chip on Android. */
export interface OptionsSearchBarScope {
  /**
   * The id of the scope, sent as `scope` in the search events and matched by `selectedScope`. Android drops a scope
   * without one.
   */
  id: string;
  /** The label of the segment or chip. On Android, the `id` without it. */
  title?: string;
  /** The identifier tests find the segment or chip by. */
  testID?: string;
}

/** (iOS) A token in front of the text of the search field. */
export interface OptionsSearchBarToken {
  /** The id of the token, listed in `tokens` of the search events. */
  id: string;
  /** The text of the token. */
  text?: string;
  /** The icon in front of the text: an SF Symbol or an image. */
  icon?: ImageResource;
  /** The identifier tests find the token by, where the system lets a token carry one. */
  testID?: string;
}

/** A suggestion shown while searching. */
export interface OptionsSearchBarSuggestion {
  /** The id of the suggestion, sent as `suggestionId` when it is picked. Android drops a suggestion without one. */
  id: string;
  /** The text of the suggestion. On Android, the `id` without it. */
  title?: string;
  /** (iOS) The description VoiceOver reads after the text; the system doesn't draw it. */
  accessibilityLabel?: string;
  /**
   * The icon in front of the text: on iOS an SF Symbol or an image, on Android a drawable resource or an image source.
   */
  icon?: ImageResource;
  /** The identifier tests find the suggestion by, where the system lets a row carry one. */
  testID?: string;
}

/**
 * The React component that is the screen of the search results. The props stay in JavaScript under the id of the
 * component; a different `id` or `name` replaces the component, and so the search.
 */
export interface OptionsSearchBarResultsComponent {
  /** The id of the component. Without it, the library gives one. The same `name` and `id` mean the same component. */
  id?: string;
  /** The name of the registered component. */
  name: string;
  /** The props of the component. They stay in JavaScript; the native side never sees them. */
  passProps?: object;
}

/**
 * The search of a screen, in the top bar. Every screen of a stack has its own search while it is in the stack; the bar
 * shows the search of the shown screen, and the text and state of the search survive push and pop. On iOS it is a
 * search controller; on Android a Material 3 search: a pill in a row under the bar, or a magnifier among the buttons,
 * which opens a field in the bar in place of the title (or a full search view over the stack when there are results
 * components). `mergeOptions` changes only the fields it carries; the lists `scopes`, `tokens` and `suggestions` are
 * replaced as a whole and an empty one removes them. `text` and `focus` apply when the screen is created or when
 * `mergeOptions` carries them, not on every appearance.
 */
export interface OptionsSearchBar {
  /**
   * Whether the screen has a search. `false` removes it and closes an open one, taking its results component away. A
   * search added to a shown screen with `hideOnScroll` starts hidden on iOS.
   * @default false
   */
  visible?: boolean;
  /**
   * `true` opens the search with the keyboard once the screen shows; `false` hides the keyboard and leaves the search
   * open. On Android a search added by `mergeOptions` opens only when the same `mergeOptions` carries `focus`.
   * @default false
   */
  focus?: boolean;
  /**
   * Whether the search field shows when the screen opens, goes away as the content scrolls and comes back at the top of
   * the list. On iOS the screen then goes under the bar by itself, and the list must keep its first rows visible
   * (`contentInsetAdjustmentBehavior="automatic"`); it doesn't work with `largeTitle.collapseOnScroll: false`. On
   * Android the bar leaves with the search, as with `topBar.hideOnScroll`, while the search is closed; not under a
   * large title.
   * @default false
   */
  hideOnScroll?: boolean;
  /**
   * (iOS) Whether the bar hides while searching and the field moves to the top. With a results component, the results
   * screen lies under the field at the top. Android's open field always takes the row of the bar.
   * @default false
   */
  hideTopBarOnFocus?: boolean;
  /**
   * Whether the screen is dimmed while searching. On Android the dimming covers the screens below the bar while the
   * open field is empty and there is no results component, and a tap on it closes the search.
   * @default false
   */
  obscuresBackgroundDuringPresentation?: boolean;
  /**
   * The color of the search field; `null` returns the system color. On iOS the library keeps the color against the
   * system resetting it under a large title. On Android it tints the pill and fills the field in the bar, by default
   * the theme's pill color.
   */
  backgroundColor?: Color;
  /** The color of the cursor and the selection; `null` returns the system color. */
  tintColor?: Color;
  /** The text of the empty field. Without it, the system "Search" in the language of the device. */
  placeholder?: string;
  /**
   * The text of the field. The screen opens with it once, when it is created, and `mergeOptions` replaces what was
   * typed. No search-updated event comes for it, and an unchanged text leaves the cursor alone. Other `mergeOptions`
   * and coming back to the screen don't reset what was typed.
   */
  text?: string;
  /**
   * Where the field is. On iOS 16+: `'stacked'` under the title; `'automatic'` as the system decides (at the bottom on
   * iPhone with iOS 26, in the bar on iPad); `'integrated'` in the bar, at the bottom or in the toolbar on iPhone with
   * iOS 26 and when there is room, else a magnifier in the bar; `'integratedButton'` the same, with a magnifier on iPad
   * always while closed; `'integratedCentered'` the same, centered in the bar on iPad, in tab controllers and with a
   * `style` `'browser'` or `'editor'`. Before iOS 26 every `'integrated…'` is a field in the bar; before iOS 16 the
   * option is not read. On Android `'stacked'` and `'automatic'` are the pill under the title, and every
   * `'integrated…'` is a magnifier last among the buttons, always in the bar.
   * @default 'stacked'
   */
  placement?: 'stacked' | 'automatic' | 'integrated' | 'integratedButton' | 'integratedCentered';
  /**
   * The filters: segments under the field (at the top of the screen when the field is at the bottom) on iOS, a row of
   * chips on Android. The search events carry the id of the selected one as `scope`, and picking one sends a
   * search-updated event. An empty list removes the strip, also from an open search. On iOS scopes added to an open
   * search show at its next opening.
   */
  scopes?: OptionsSearchBarScope[];
  /**
   * The id of the selected scope; without it, or with an unknown id, the first. The user's pick is written into the
   * options of the screen and stays after coming back. `mergeOptions` switches the scope without an event.
   */
  selectedScope?: string;
  /**
   * When the strip of scopes shows. On iOS 16+: `'automatic'` (on iOS 26, once the user types), `'onTextEntry'`,
   * `'onSearchActivation'`; before iOS 16 while searching. On Android `'automatic'` and `'onSearchActivation'` show it
   * as soon as the search is open, `'onTextEntry'` once the field has text.
   * @default 'automatic'
   */
  scopeBarActivation?: 'automatic' | 'onTextEntry' | 'onSearchActivation';
  /**
   * (iOS) Tokens in front of the text of the field. The search events carry the ids of the tokens in the field as
   * `tokens`; deleting a token sends a search-updated event, and the deleted tokens (and ones brought back with Undo)
   * are written to the options of the screen. Closing the search clears the field and its tokens. An empty list removes
   * them all.
   */
  tokens?: OptionsSearchBarToken[];
  /**
   * (iOS 16+, Android) The suggestions shown while searching. Picking one sends a suggestion-picked event with its id
   * to the screen; the text stays and the list goes. They also go when the user types on, and new ones come with
   * `mergeOptions`, usually on a search-updated event. An empty list removes them.
   */
  suggestions?: OptionsSearchBarSuggestion[];
  /**
   * A screen of its own for the results: a React component over the screen from the moment the search opens
   * (`resultsActivation`) until it closes. It gets the search events with its own component id, and the lifecycle
   * events on each show and hide. A different component makes a new search, closing an open one.
   */
  resultsComponent?: OptionsSearchBarResultsComponent;
  /**
   * When the results component shows: `'onSearchActivation'` as soon as the search opens, also with an empty field;
   * `'onTextEntry'` while the field has text, the screen being visible before. A change by `mergeOptions` while the
   * search is open applies at once.
   * @default 'onSearchActivation'
   */
  resultsActivation?: 'onSearchActivation' | 'onTextEntry';
  /**
   * The identifier tests find the search field by (the pill, the field in the bar, the search view field, the
   * magnifier). The magnifier of the integrated placements gets `<testID>.button`, the cancel button `<testID>.cancel`
   * and the clear button `<testID>.clear`; on iOS the system draws the cancel button, so it may not take one.
   */
  testID?: string;
}

/**
 * The bar at the top of a screen in a stack. It is one for the stack and shows the options of the shown screen. The
 * options apply whenever the screen shows, and on `mergeOptions` of the shown screen; a `mergeOptions` of a screen that
 * is not shown waits for its appearance. Colors follow a light or dark switch. A screen without a stack (a modal
 * without a `Stack`, an overlay) has no bar, and its `topBar` options do nothing. Rotation doesn't re-apply the
 * options, but the layout of the buttons and the status bar strip on Android and the width of a filling title on iOS
 * follow the size of the window.
 */
export interface OptionsTopBar {
  /**
   * Whether the bar shows. It applies on each appearance and on `mergeOptions`; the change is animated unless `animate`
   * is `false`. A hidden bar puts the screen under it and shows no large title; on iOS the top safe area inset comes
   * from `visible: false` only, not from a bar hidden by scrolling. On Android hiding slides the bar up and then
   * removes it; on push and pop it moves with the transition, and showing resets the `hideOnScroll` offset.
   * @default true
   */
  visible?: boolean;
  /**
   * Whether the change of `visible` is animated, also in `mergeOptions`. On Android `false` applies the visibility at
   * once on push, pop and `setStackRoot`.
   * @default true
   */
  animate?: boolean;
  /**
   * Whether the bar goes away when the content is scrolled down and comes back on scrolling up. The screen goes under
   * the bar by itself, so the list must keep its first rows clear of it (iOS
   * `contentInsetAdjustmentBehavior="automatic"`, Android `paddingTop` of the top safe area inset). On iOS before 27 it
   * is the stack's hide-on-swipe, and the swipe back keeps working; on iOS 27+ it is the system minimization of the bar
   * on scroll down, and without the option the bar never minimizes. On Android it doesn't work under a large title or
   * while a search is open. A change by `mergeOptions` applies at once.
   * @default false
   */
  hideOnScroll?: boolean;
  /**
   * (iOS) Whether a tap on the screen that nothing handled hides the bar, and the next tap shows it. The value of the
   * shown screen applies on each appearance, so a screen below keeps its own after coming back.
   * @default false
   */
  hideOnTap?: boolean;
  /**
   * (iOS) Whether the bar hides while the keyboard is up and returns when it closes, unless the screen hides the bar
   * with `visible: false`.
   * @default false
   */
  hideOnKeyboard?: boolean;
  /**
   * The color of the left buttons that have no `color` of their own. Without it, the tint of the bar on iOS, the
   * theme's text color on Android with icons untinted. The buttons get copies with the color, so a color doesn't stay
   * in buttons shared with other screens. `mergeOptions` repaints the shown buttons and keeps the color in the screen's
   * own buttons through the next appearance and a theme switch. On iOS it sets the text color and the tint; an SF
   * Symbol without a color is drawn as a template, `disableIconTint` keeps its colors.
   */
  leftButtonColor?: Color;
  /**
   * The same for the right buttons, including pinned ones on iOS. On Android it also colors the overflow icon (the
   * theme's on-surface-variant color without it).
   */
  rightButtonColor?: Color;
  /**
   * The shape color behind the icons of the left buttons that have an `icon` image and no `iconBackground.color`; the
   * size and corners come from the button's `iconBackground`, or the icon's size without corners. On iOS only image
   * buttons, not SF Symbols, and without a capsule on iOS 26. A color sent by `mergeOptions` repaints all the side's
   * image buttons, those with their own shape color too.
   */
  leftButtonBackgroundColor?: Color;
  /** The same for the right buttons. */
  rightButtonBackgroundColor?: Color;
  /**
   * The color of the disabled left buttons that have no `disabledColor` of their own. On iOS the text color and the
   * tint of the icon (without it the system dims them); on Android the text and icon tint, light gray by default.
   */
  leftButtonDisabledColor?: Color;
  /** The same for the right buttons. */
  rightButtonDisabledColor?: Color;
  /** (iOS 16+) The color of the center buttons that have no `color` of their own, handled like `leftButtonColor`. */
  centerButtonColor?: Color;
  /** (iOS 16+) The color of the disabled center buttons that have no `disabledColor` of their own. */
  centerButtonDisabledColor?: Color;
  /**
   * Whether the content of the screen goes under the bar, starting at the top of the screen. The screen goes under the
   * bar by itself in some cases (a large title that may show, `hideOnScroll`, a hidden bar, an explicit translucent
   * background on iOS). A change by `mergeOptions` applies at once on iOS, on the next layout on Android.
   * @default false
   */
  drawBehind?: boolean;
  /**
   * The identifier tests find the bar by: the accessibility identifier of the bar on iOS (without it UIKit gives the
   * title of the screen), the view tag on Android. The text title gets `<testID>.title`, the subtitle
   * `<testID>.subtitle` and the large title `<testID>.largeTitle`. The system title of iOS 26 can't carry an
   * identifier; find it by its text inside the bar. Without the option, the identifier is cleared.
   */
  testID?: string;
  /** The title: text and font, or a component, and on iOS its menu. */
  title?: OptionsTopBarTitle;
  /** The second line under the title. */
  subtitle?: OptionsTopBarSubtitle;
  /** The back button of the screen. */
  backButton?: OptionsTopBarBackButton;
  /**
   * The buttons at the leading edge, the first at the edge. The list is replaced as a whole and `[]` removes the
   * buttons; they take nothing from the lists of the default options or the stack. They replace the back button on both
   * platforms; on Android a first button with an `id` also removes the arrow. `mergeOptions` animates the change with
   * `animateLeftButtons`.
   */
  leftButtons?: OptionsTopBarButton[];
  /**
   * The buttons at the trailing edge, the first at the edge. The list is replaced as a whole. When space runs out iOS
   * first removes the title, then moves right buttons to the overflow; on Android the bar keeps the buttons of the
   * highest `priority`, and the rest go to the overflow menu.
   */
  rightButtons?: OptionsTopBarButton[];
  /**
   * (iOS 16+) The buttons in the middle of the bar, in the format of the side buttons. A button, or neighbors with the
   * same `group`, is one group (one capsule). They show in the middle only on iPad with a `style` of `'browser'` or
   * `'editor'`; on iPhone, in `'navigator'` and while the tab bar of iPad sits in the bar they are in the overflow
   * menu, a section per group, where an icon button needs a `text`. Toggles and menus of center buttons keep their
   * state.
   */
  centerButtons?: OptionsTopBarButton[];
  /**
   * (iOS 16+) Lets the user customize the center buttons on iPad and Mac, under this name; the system saves the choice.
   * A group is customizable by its `group` name (several buttons) or by the `id` of the button; one without a name, or
   * with a taken one, is fixed. The panel opens from the system menu of iPadOS 26 (View, Customize Toolbar). Empty
   * means no customization, always on iPhone. The list of buttons in the options doesn't change and no callback comes.
   */
  customizationId?: string;
  /**
   * The overflow menu ("•••" on iOS 16+, the vertical dots on Android): its items come above the buttons the platform
   * moved there, and the overflow button always shows with them. A pick sends a button press with its id. `items: []`
   * removes the items. On iOS the menu is built from the options at each opening, and a title is the title of its
   * section; before iOS 16 there is none. On Android the items come first, always in the menu, with icons, destructive
   * and disabled states, checks and submenus, and the overflow takes the place of one button, so the buttons are laid
   * out again.
   */
  overflowMenu?: OptionsTopBarOverflowMenu;
  /**
   * (iOS 27.1+) Who keeps the space in the vertical bar of iPhone Duo, where tabs and the buttons of the screen share
   * it: `'tabs'` keeps the tabs and moves the buttons to the overflow, `'buttons'` keeps the buttons and compresses the
   * tabs. Without it the system keeps the tabs. For a pair of screens the option of the primary one counts.
   */
  verticalBarKeeps?: 'tabs' | 'buttons';
  /** The background of the bar: color, component, translucency, blur. */
  background?: OptionsTopBarBackground;
  /** (iOS) The look of the bar while the list is at its top edge. Android doesn't read it. */
  scrollEdgeAppearance?: OptionsTopBarScrollEdgeAppearance;
  /**
   * (iOS) The bar style of the stack. What shows of it on a bar with the current appearance the system decides; the
   * option matters with `background.translucent: true` only. It doesn't give the status bar its style: the status bar
   * follows `statusBar.style` of the screen on top.
   * @default 'default'
   */
  barStyle?: 'default' | 'black';
  /**
   * (iOS 16+) The layout of the bar: `'navigator'` centers the title; `'browser'` and `'editor'` put it next to the
   * back button and leave the middle for the center buttons on iPad. On iOS 26 the two look the same. Without it the
   * system style (`'navigator'`) is left alone.
   */
  style?: 'navigator' | 'browser' | 'editor';
  /**
   * (iOS) Whether there is no line under the bar in its usual look. It survives a `mergeOptions` of the background. At
   * the scroll edge `scrollEdgeAppearance.noBorder` counts.
   * @default false
   */
  noBorder?: boolean;
  /** The search of the screen, in the bar. */
  searchBar?: OptionsSearchBar;
  /** The large title. */
  largeTitle?: OptionsTopBarLargeTitle;
  /**
   * (Android) The height of the row of the bar, in dp. The status bar strip comes on top of it, and a large title and a
   * search below. The row of the title alone is `title.height`.
   * @default 64
   */
  height?: number;
  /**
   * The color of the line under the bar. On iOS it is the shadow color of the standard look, the system one without it,
   * and it does nothing with `noBorder: true`. On Android it is the color of the line of `borderHeight`, black by
   * default and invisible at height 0. A change by `mergeOptions` applies at once. At the scroll edge,
   * `scrollEdgeAppearance.borderColor`.
   */
  borderColor?: Color;
  /**
   * (Android) The height of the line under the bar, in dp.
   * @default 0
   */
  borderHeight?: number;
  /**
   * (Android) The shadow of the bar, in dp.
   * @default 0
   */
  elevation?: number;
  /**
   * (Android) The space above the bar, in dp: the bar starts lower. When the bar is not under the status bar and the
   * status bar shows, its height is added. It also lengthens the way of the show and hide animation.
   * @default 0
   */
  topMargin?: number;
  /**
   * Whether a change of the left buttons of the shown screen is animated: on iOS the new buttons fade in, but a side
   * with a group of several buttons changes at once; on Android it is a transition of the left bar. On iOS 26 and
   * later the system cross-fades a change of the bar's buttons by itself, also when this is `false`.
   * @default false
   */
  animateLeftButtons?: boolean;
  /**
   * Whether a change of the right buttons of the shown screen is animated, like `animateLeftButtons`.
   * @default false
   */
  animateRightButtons?: boolean;
}

/**
 * The overflow menu of the bar: a menu in the format of the button menu, with an identifier for tests of the button
 * that opens it.
 */
export interface OptionsTopBarOverflowMenu extends OptionsTopBarButtonMenu {
  /**
   * The identifier tests find the overflow button by. On Android it is the view of the dots; on iOS the system draws
   * the "•••" button, so it may not carry the identifier.
   */
  testID?: string;
}

/** The style of the scroll edge effect of an edge. */
export type ScrollEdgeEffectStyle = 'automatic' | 'soft' | 'hard';

/** (iOS 26+) The scroll edge effect of one edge of the scroll views of a screen. */
export interface OptionsScrollEdge {
  /** Whether the effect of this edge is hidden. Without it, the value of `scrollEdgeEffect.hidden`. */
  hidden?: boolean;
  /**
   * The style of this edge: `'automatic'`, `'soft'` or `'hard'`. Without it, `scrollEdgeEffect.style`, and without
   * that, `'automatic'`.
   */
  style?: ScrollEdgeEffectStyle;
}

/**
 * (iOS 26+) The scroll edge effect of the scroll views of a screen: how the content fades or blurs where it scrolls
 * under a bar. The scroll views draw it, and only where content goes under a bar. It applies to every scroll view in
 * the view of the screen when the screen appears; after React mounts it applies to the main list of the screen only; on
 * `mergeOptions` all is reset and applied again. Other scroll views mounted later get it at the next appearance or
 * `mergeOptions`. When the screen leaves, its scroll views get the system effect back. Under a bar with
 * `background.color` or `background.component` there is no effect at the top unless the screen sets one for the top or
 * for all edges. Before iOS 26 and on Android it does nothing.
 */
export interface OptionsScrollEdgeEffect {
  /** Whether the effect is hidden on all four edges. The value of an edge beats it. */
  hidden?: boolean;
  /**
   * The style on all edges: `'automatic'`, `'soft'` or `'hard'`. The value of an edge beats it.
   * @default 'automatic'
   */
  style?: ScrollEdgeEffectStyle;
  /** The top edge; without values, the common ones. */
  top?: OptionsScrollEdge;
  /** The bottom edge; without values, the common ones. */
  bottom?: OptionsScrollEdge;
  /** The left edge; without values, the common ones. */
  left?: OptionsScrollEdge;
  /** The right edge; without values, the common ones. */
  right?: OptionsScrollEdge;
}
