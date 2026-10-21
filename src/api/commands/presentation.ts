import type {
  ModalOptions,
  OptionsModalPopover,
  OptionsModalPresentationStyle,
  OptionsModalSheet,
} from '../../interfaces/Options';
import type { ScreenOptionsInput } from '../screens/defineScreen';
import type { Transition } from './transitions';

/** How `push` shows a screen. */
export interface PushConfig {
  /**
   * How the screen comes and goes: a preset (`'fade'`, `'slideUp'`, `'scale'`…), the zoom of iOS 18 out of a view, or
   * views that move into the screen. The way back is the same in reverse. `options.animations` go over it.
   */
  transition?: Transition;
  /** Options of the screen for this show, over those of its definition. */
  options?: ScreenOptionsInput<object>;
}

/** How `showInColumn` shows a screen. */
export interface ColumnConfig {
  /**
   * The column: `'secondary'`, the one beside the sidebar, or `'supplementary'`, the middle one of three. Android has
   * the secondary column only.
   */
  column?: 'secondary' | 'supplementary';
  /** Options of the screen for this show, over those of its definition. */
  options?: ScreenOptionsInput<object>;
}

/** How `showModal` shows a screen. */
export interface ModalConfig {
  /** The presentation style of the modal. */
  style?: `${OptionsModalPresentationStyle}`;
  /** A sheet with detents (on Android the Material bottom sheet); it is a page sheet unless `style` says otherwise. */
  sheet?: OptionsModalSheet;
  /** #### (iOS specific) A popover from a button or a view; it is a popover unless `style` says otherwise. */
  popover?: OptionsModalPopover;
  /** #### (iOS specific) The size of a form sheet. */
  contentSize?: ModalOptions['contentSize'];
  /** `false`: the screen shows without a stack around it, so without a top bar. */
  stack?: boolean;
  /**
   * A close button at the leading edge of the modal's first screen, which dismisses the modal: the system one on iOS,
   * an icon on Android. Its id and testID are `CloseButton`. Needs the stack.
   */
  closeButton?: boolean;
  /**
   * Called once the modal is gone, whatever dismissed it: a command, a swipe, a new root. `setStack` inside the
   * modal replaces its first screen, and calls it too.
   */
  onDismiss?: () => void;
  /**
   * How the screen comes and goes: a preset (`'fade'`, `'slideUp'`, `'scale'`…), the zoom of iOS 18 out of a view, or
   * views that move into the screen. The way back is the same in reverse. `options.animations` go over it.
   */
  transition?: Transition;
  /** Options of the screen for this show, over those of its definition. */
  options?: ScreenOptionsInput<object>;
}

/**
 * How `showOverlay` shows a screen; its background is transparent. Touches outside its views reach the app under it.
 */
export interface OverlayConfig {
  /** Touches outside the overlay's views don't reach the app: a small overlay that still stops it. */
  blockTouches?: boolean;
  /** Options of the screen for this show, over those of its definition. */
  options?: ScreenOptionsInput<object>;
}

/** What every push, modal and overlay of the app starts from; the settings of a command go over it. */
export interface Presentation {
  push?: PushConfig;
  modal?: Omit<ModalConfig, 'onDismiss'>;
  overlay?: OverlayConfig;
}
