/**
 * How the platform presents a modal: `'pageSheet'`, `'formSheet'`, `'fullScreen'`, `'overFullScreen'`,
 * `'overCurrentContext'`, `'currentContext'` or `'popover'`. It is read once, when the modal shows, from the modal's
 * final options; `mergeOptions` afterwards doesn't change it, and neither does a change of theme or a rotation. Without
 * it the platform decides: on iOS the automatic style of UIKit (a sheet on iPhone, a card on iPad), on Android the
 * whole screen. An unknown string shows a full-screen modal on iOS and counts as not set on Android.
 *
 * - `'pageSheet'`: (iOS) a sheet that leaves the screen below visible and pushed back; on iPad a wide card. Android
 *   shows the whole screen, or the Material bottom sheet when `modal.sheet` is set.
 * - `'formSheet'`: (iOS) a sheet on iPhone, a card in the middle on iPad whose size is `modal.contentSize`. Android
 *   shows the whole screen, or the bottom sheet when `modal.sheet` is set.
 * - `'fullScreen'`, `'currentContext'`: the whole screen; the screen below is taken off. Both platforms treat the two
 *   alike.
 * - `'overFullScreen'`, `'overCurrentContext'`: the whole screen with the screen below kept in place. It shows
 *   through only where the modal screen is transparent (`layout.backgroundColor` and `layout.componentBackgroundColor`
 *   both `'transparent'`); the style does not make anything transparent by itself.
 * - `'popover'`: (iOS) a popover with an arrow on iPad, a sheet on iPhone unless `modal.popover.adaptive` is `false`.
 *   Android has no popovers and shows the whole screen.
 *
 * The options of the sheet (`modal.sheet`) apply with `'pageSheet'`, `'formSheet'` and without a style.
 */
export type OptionsModalPresentationStyle =
  | 'pageSheet'
  | 'formSheet'
  | 'fullScreen'
  | 'overFullScreen'
  | 'overCurrentContext'
  | 'currentContext'
  | 'popover';

/**
 * (iOS) The system transition a modal comes and goes with: `'coverVertical'` (from below), `'crossDissolve'` (a fade),
 * `'flipHorizontal'` (a flip) or `'partialCurl'`. It is read when the modal shows and doesn't change afterwards;
 * `animations.showModal` with values of its own, and the zoom of iOS 18, go over it. UIKit flips only a modal over the
 * whole screen (`'fullScreen'`, `'overFullScreen'`): a sheet comes in as usual and flips only on its way out, and
 * `'partialCurl'` is accepted by UIKit only for a full-screen modal. An unknown string means `'coverVertical'`. Android
 * doesn't read it.
 */
export type OptionsModalTransitionStyle =
  'coverVertical' | 'crossDissolve' | 'flipHorizontal' | 'partialCurl';

/**
 * A stop of a sheet: `'medium'` (about half of the screen), `'large'` (the full height), a number (a height in points
 * on iOS, in dp on Android) or `'content'` (the height of the screen's content).
 *
 * On iOS `'medium'` and `'large'` are the system stops; a number and `'content'` need iOS 16 and are left out below it
 * with a warning in the log. On Android `'medium'` is half of the window, `'large'` the full height under the status
 * bar, a number is capped at `'large'`, and `'content'` stands in as `'medium'` because the Material sheet has no
 * height by content.
 */
export type OptionsModalSheetDetent = 'medium' | 'large' | 'content' | number;

/**
 * The settings of a sheet: a modal that comes from below and rests on one of several heights, its stops (detents). On
 * iOS they configure the system sheet of a modal presented as `'pageSheet'`, `'formSheet'` or without a style. On
 * Android any one of them turns the modal into a Material bottom sheet, again for those styles only. They are read when
 * the modal shows, and a `mergeOptions` on any screen of the modal changes them while it is shown, with an animation.
 * Objects merge key by key; `detents` is replaced as a whole.
 */
export interface OptionsModalSheet {
  /**
   * The stops the sheet can rest on, and the user drags it between. On Android they are sorted by height and repeats
   * dropped; the Material sheet has three resting places at most: the lowest stop of the list, the highest, and
   * `'medium'` between them when the list has both `'medium'` and `'large'`. A stop without a place is dropped. On iOS
   * the stops are handed to UIKit in the order given, which wants them from the lowest to the highest; a list left
   * empty by unsupported values gives `['large']`.
   * @default ['large']
   */
  detents?: OptionsModalSheetDetent[];
  /**
   * The stop the sheet opens on; a new value in `mergeOptions` moves the sheet there with an animation, and doesn't
   * send `sheetDetentChanged`. A value that isn't among `detents` opens on the lowest stop on iOS and is ignored on
   * Android.
   * Without it, the lowest stop.
   */
  selectedDetent?: OptionsModalSheetDetent;
  /**
   * The highest stop on which the screen below the sheet stays bright and takes touches, like the sheet of a map. On
   * Android the dimming grows from none at this stop to a dark veil at the next higher one, and a tap on the dimmed
   * part closes the sheet. `null`, or a value that isn't among `detents`, dims at every stop. Without it, the same.
   */
  largestUndimmedDetent?: OptionsModalSheetDetent | null;
  /**
   * Whether the sheet shows a handle at the top to drag it by: the system handle on iOS, the Material drag handle on
   * Android.
   * @default false
   */
  grabber?: boolean;
  /**
   * Whether a scroll in the content that starts at the top of a list first moves the sheet: a drag up raises the sheet
   * to a higher stop, a drag down lowers it. With `false` the content scrolls by itself and the sheet is dragged by
   * its top only (on Android the top 48 dp strip). On Android the sheet turns on nested scrolling for the React Native
   * scroll views inside it to do this.
   * @default true
   */
  expandsOnScroll?: boolean;
  /**
   * The corner radius of the sheet, in points on iOS and dp on Android. On iOS UIKit uses it only when the sheet is
   * the front one of a pile of sheets; on Android only the top corners are rounded. Without it, the system radius
   * (28 dp on Android).
   */
  cornerRadius?: number;
  /**
   * The test identifier of the sheet's container on Android. The handle gets `<testID>.grabber` (below Android 10 as
   * its tag only, without a resource name) and the dimming under the sheet `<testID>.scrim`. iOS draws the sheet, its
   * handle and its dimming itself, and gives no public way to name them; there find the sheet by its content, and the
   * content of the screen carries its own `testID`.
   */
  testID?: string;
}

/** A side of a popover's arrow. */
export type PopoverArrowDirection = 'up' | 'down' | 'left' | 'right';

/**
 * (iOS) The size of a modal shown as a form sheet or a popover, where UIKit doesn't stretch it over the screen (iPad,
 * Mac). It is the preferred content size of the modal's root. It applies when the modal shows, once more just after it
 * has shown, after the content mounts when the height is `'content'`, after a push or pop in the modal's stack, and
 * after `mergeOptions` on any screen of the modal. On iPhone a form sheet is a sheet and the size does nothing.
 */
export interface OptionsModalContentSize {
  /** The width in points. Without it, or `0`, the width UIKit gives the style. */
  width?: number;
  /**
   * The height in points, or `'content'` for the height of the content of the screen the modal shows, measured at the
   * modal's width (`width`, otherwise its current width) and followed as the content changes. Without it, the height
   * UIKit gives the style.
   */
  height?: number | 'content';
}

/**
 * (iOS) Where a popover points and how it behaves, for a modal shown with `modalPresentationStyle: 'popover'`. It is
 * read when the modal shows and doesn't change afterwards. The source is looked up on the presenting layout, the
 * topmost one shown (the root or the top modal). Without a source, or when the source isn't found, the popover stands
 * in the middle of the presenting screen without an arrow, and a missing source that was named is reported in the log.
 * Tapping outside the popover dismisses it, or sends `modalDismissAttempted` while the modal is held. Android has no
 * popovers and ignores this.
 */
export interface OptionsModalPopover {
  /**
   * The `id` of a button of the top bar or the toolbar of the presenting screen to point at. It is looked up in every
   * screen shown by the presenting layout (in a split view, in every visible column), among left and right buttons,
   * toolbar buttons and button groups. From iOS 16 the popover attaches to the button as a source item, and on iOS 26 it
   * opens out of the button. It goes before `sourceNativeID`.
   */
  sourceButtonId?: string;
  /**
   * The `nativeID` of a React view of the presenting layout to point at; the arrow points at the frame of the view.
   * It is looked up in the whole view hierarchy of the presenting layout.
   */
  sourceNativeID?: string;
  /**
   * The sides the arrow may point from. Unknown values are skipped. Without it, or with an empty list, any side. With
   * no source there is no arrow.
   */
  arrowDirections?: PopoverArrowDirection[];
  /**
   * Whether UIKit may turn the popover into a sheet in a compact width (iPhone, a narrow window of iPad). With
   * `false` the popover keeps its arrow there too.
   * @default true
   */
  adaptive?: boolean;
}

/**
 * The options of a screen shown as a modal. They are read from the final options of the modal when it shows: the
 * options of its root, over them the options of its visible screen, over them the default options. On a screen that
 * isn't in a modal they do nothing. A value of the wrong type is skipped without a crash.
 */
export interface ModalOptions {
  /** The settings of a sheet: its stops, dimming, handle, scrolling and corners. */
  sheet?: OptionsModalSheet;
  /** (iOS) The size of a form sheet or a popover on iPad and Mac. */
  contentSize?: OptionsModalContentSize;
  /** (iOS) The source and the arrow of a popover. */
  popover?: OptionsModalPopover;
  /**
   * The test identifier of the modal's container: on iOS the view of the root of the shown modal, on Android the
   * modal's view in the layer of modals (for a sheet, the container of the sheet). The identifiers of the sheet's
   * handle and of the dimming under it are `<testID>.grabber` and `<testID>.scrim`, on Android only: iOS gives no
   * public way to name the system handle and dimming. The close button and the content of the screens have their own
   * `testID`.
   */
  testID?: string;
  /**
   * Whether the user may dismiss the modal by a gesture: a swipe down on a sheet, a tap on the dimming, a tap outside a
   * popover. With `false` the modal stays and each attempt arrives as `modalDismissAttempted`. A full-screen modal
   * can't be dismissed by a gesture anyway. The hold belongs to the screen that set it and ends when that screen
   * leaves the modal or lifts the hold. Set by `usePreventLeave` through `mergeOptions`; apps don't set it.
   * @default true
   * @internal
   */
  swipeToDismiss?: boolean;
}

/**
 * The options of a screen shown as an overlay: a screen over the whole app that takes the place of no screen, such as
 * a toast, a banner or a mini player. They are read from the final options of the overlay's root with the default
 * options, and can change through `mergeOptions` while the overlay shows. On a screen that isn't in an overlay they do
 * nothing.
 */
export interface OverlayOptions {
  /**
   * Whether the overlay keeps the touches that land outside its own views. With `false` those touches reach the app
   * under the overlay; with `true` they stop at the overlay. A touch counts as inside when it lands on a view of the
   * content that takes touches, so a full-screen wrapper with `pointerEvents="box-none"` lets touches through. A
   * dialog with a dimmed background gets the touches on the background by being a view of its own.
   * @default false
   */
  interceptTouchOutside?: boolean;
  /**
   * The test identifier of the overlay's container: on iOS the root view of the overlay's window, on Android the
   * overlay's view in the layer of overlays. The content of the screen has its own `testID`.
   */
  testID?: string;
}
