import { Dimensions, I18nManager } from 'react-native';
import type { AnimationOptions, ScreenAnimationOptions } from '../../interfaces/Options';

/** A ready transition: how a screen comes, and the same in reverse when it goes. */
export type TransitionPreset = 'platform' | 'none' | 'fade' | 'slideUp' | 'slideFromEnd' | 'scale';

/**
 * How a screen comes and goes; the way back is the same in reverse. A preset, a preset with its duration, the zoom of
 * iOS 18 out of the view with a `nativeID`, or views that move from this screen into the new one (`nativeID` pairs,
 * with a fade of the rest unless `type` says otherwise).
 */
export type Transition =
  | TransitionPreset
  | { type: TransitionPreset; duration?: number }
  | { zoom: string }
  | { shared: [from: string, to: string][]; type?: TransitionPreset; duration?: number };

const DURATION = 300;

interface Parts {
  preset: TransitionPreset;
  duration: number;
  zoom?: string;
  shared?: [string, string][];
}

function parts(transition: Transition): Parts {
  if (typeof transition === 'string') return { preset: transition, duration: DURATION };
  if ('zoom' in transition)
    return { preset: 'platform', duration: DURATION, zoom: transition.zoom };
  if ('shared' in transition) {
    return {
      preset: transition.type ?? 'fade',
      duration: transition.duration ?? DURATION,
      shared: transition.shared,
    };
  }
  return { preset: transition.type, duration: transition.duration ?? DURATION };
}

/**
 * The content of a preset as the screen comes (`show`) and as it goes (`hide`); none for `platform` and `none`.
 * `slideFromEnd` comes from the right, or from the left in a right-to-left layout.
 */
export function presetContent(
  preset: TransitionPreset,
  duration: number = DURATION
): { show: ScreenAnimationOptions; hide: ScreenAnimationOptions } | undefined {
  const { width, height } = Dimensions.get('window');
  const end = I18nManager.isRTL ? -width : width;
  switch (preset) {
    case 'fade':
      return {
        show: { alpha: { from: 0, to: 1, duration } },
        hide: { alpha: { from: 1, to: 0, duration } },
      };
    case 'slideUp':
      return {
        show: { translationY: { from: height, to: 0, duration } },
        hide: { translationY: { from: 0, to: height, duration } },
      };
    case 'slideFromEnd':
      return {
        show: { translationX: { from: end, to: 0, duration } },
        hide: { translationX: { from: 0, to: end, duration } },
      };
    case 'scale':
      return {
        show: {
          scaleX: { from: 0.85, to: 1, duration },
          scaleY: { from: 0.85, to: 1, duration },
          alpha: { from: 0, to: 1, duration },
        },
        hide: {
          scaleX: { from: 1, to: 0.85, duration },
          scaleY: { from: 1, to: 0.85, duration },
          alpha: { from: 1, to: 0, duration },
        },
      };
    default:
      return undefined;
  }
}

function sharedElements(pairs: [string, string][] | undefined, duration: number, back: boolean) {
  if (!pairs?.length) return undefined;
  return pairs.map(([from, to]) =>
    back ? { fromId: to, toId: from, duration } : { fromId: from, toId: to, duration }
  );
}

/** The animations of a push and of the pop of the screen it shows. */
export function pushAnimations(transition: Transition | undefined): AnimationOptions | undefined {
  if (transition === undefined) return undefined;
  const { preset, duration, zoom, shared } = parts(transition);
  if (zoom) return { push: { zoom: { fromId: zoom } } };
  if (preset === 'none') return { push: { enabled: false }, pop: { enabled: false } };
  const content = presetContent(preset, duration);
  const forth = sharedElements(shared, duration, false);
  if (!content && !forth) return undefined;
  return {
    push: defined({ content: content && { enter: content.show }, sharedElementTransitions: forth }),
    pop: defined({
      content: content && { exit: content.hide },
      sharedElementTransitions: sharedElements(shared, duration, true),
    }),
  };
}

/** The animations of a modal as it shows and as it is dismissed. */
export function modalAnimations(transition: Transition | undefined): AnimationOptions | undefined {
  if (transition === undefined) return undefined;
  const { preset, duration, zoom, shared } = parts(transition);
  if (zoom) return { showModal: { zoom: { fromId: zoom } } };
  if (preset === 'none') {
    return { showModal: { enter: { enabled: false } }, dismissModal: { exit: { enabled: false } } };
  }
  const content = presetContent(preset, duration);
  const forth = sharedElements(shared, duration, false);
  if (!content && !forth) return undefined;
  return {
    showModal: defined({ enter: content?.show, sharedElementTransitions: forth }),
    dismissModal: defined({
      exit: content?.hide,
      sharedElementTransitions: sharedElements(shared, duration, true),
    }),
  };
}

/** The object without its undefined values, so they don't go to the native side. */
export function defined<T extends object>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined)) as T;
}
