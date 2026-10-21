import type { LayoutRoot } from '../../interfaces/Layout';
import type { EnterExitAnimationOptions } from '../../interfaces/Options';
import { presetContent, type TransitionPreset } from '../commands/transitions';
import {
  buildContent,
  isLayoutElement,
  type LayoutContent,
  layoutOptions,
  mergeLayoutOptions,
  type ScreenUse,
  type ShowsScreens,
  type UsesOf,
} from './element';

/**
 * How a root replaces the one before: a preset the new root comes in with, or its own `enter` and `exit`. `'platform'`
 * is the platform's own transition, `'none'` shows the new root at once, once its content is drawn, whatever setRoot
 * animation the app's options set.
 */
export type RootTransition = TransitionPreset | EnterExitAnimationOptions;

const ROOT_DURATION = 250;

function rootAnimation(transition: RootTransition): EnterExitAnimationOptions | undefined {
  if (typeof transition !== 'string') return transition;
  // At once, but not empty: without the wait Android would show the new root before React draws it. Both sides are off,
  // so a setRoot animation of the app's default options doesn't play either.
  if (transition === 'none') {
    return { enter: { enabled: false, waitForRender: true }, exit: { enabled: false } };
  }
  const content = presetContent(transition, ROOT_DURATION);
  return content && { enter: content.show };
}

const BUILD_ROOT = Symbol('apexnative.navigation.buildRoot');

/** A root with the modals shown over it, and how it replaces the one before. */
export class RootElement<Uses extends ScreenUse = ScreenUse> {
  constructor(
    private readonly root: LayoutContent,
    private readonly modals: readonly LayoutContent[] = [],
    private readonly rootTransition: RootTransition = 'platform'
  ) {}

  /**
   * A modal over the root once it is in place, such as the screen a notification opens at a cold start. It comes with
   * its animation; each one goes over the one before, and the ones under the last come at once.
   */
  modal<const Content extends LayoutContent>(modal: Content): RootElement<Uses | UsesOf<Content>> {
    return new RootElement(this.root, [...this.modals, modal], this.rootTransition);
  }

  /**
   * How the root replaces the one before: `'platform'` (the default), `'fade'`, `'slideUp'`, `'slideFromEnd'` or
   * `'scale'` (250 ms), `'none'` at once, or `{ enter, exit }` of its own. `'platform'` is the platform's transition: a
   * cross dissolve on iOS, the fade through on Android. The first root of the app comes in place of the splash screen:
   * at once with `'platform'` and `'none'`, over the splash with a preset or `{ enter, exit }`.
   */
  transition(transition: RootTransition): RootElement<Uses> {
    return new RootElement(this.root, this.modals, transition);
  }

  [BUILD_ROOT](): LayoutRoot {
    const animation = rootAnimation(this.rootTransition);
    const root = buildContent(this.root, 'stack');
    return {
      root: animation ? mergeLayoutOptions(root, { animations: { setRoot: animation } }) : root,
      ...(this.modals.length > 0 && {
        modals: this.modals.map((modal) => buildContent(modal, 'stack')),
      }),
    };
  }
}

// The type of an element carries the screens it shows.
export interface RootElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}

export function Root<const Content extends LayoutContent>(
  root: Content
): RootElement<UsesOf<Content>> {
  return new RootElement(root);
}

/** What `root`, `roots` and `switchTo` take: content, `Root(…)`, or a root as JSON. */
export type RootLayout = LayoutContent | RootElement<any> | LayoutRoot;

/** The screens a root shows; a root as JSON is not checked. */
export type UsesOfRoot<Value> = Value extends RootElement<infer Uses> ? Uses : UsesOf<Value>;

export function buildRoot(value: RootLayout): LayoutRoot {
  if (value instanceof RootElement) return value[BUILD_ROOT]();
  if (isLayoutRoot(value)) return value;
  return { root: buildContent(value, 'stack') };
}

/**
 * The root with `transition` when it has no setRoot animation of its own: how the first root replaces the app's
 * splash in React Native. It comes in once its content is drawn, unless the transition says otherwise: Android
 * otherwise starts a root's own animation at once, over a screen still empty.
 */
export function withDefaultTransition(layout: LayoutRoot, transition: RootTransition): LayoutRoot {
  const animation = rootAnimation(transition);
  if (!animation || layoutOptions(layout.root)?.animations?.setRoot) return layout;
  const enter = { ...animation.enter, waitForRender: animation.enter?.waitForRender ?? true };
  return {
    ...layout,
    root: mergeLayoutOptions(layout.root, { animations: { setRoot: { ...animation, enter } } }),
  };
}

function isLayoutRoot(value: RootLayout): value is LayoutRoot {
  return typeof value === 'object' && !isLayoutElement(value) && 'root' in value;
}
