import type { Layout } from '../../interfaces/Layout';
import type { Options } from '../../interfaces/Options';
import { mergeOptions } from '../../engine/mergeOptions';
import { buildScreenOptions, type ScreenOptionsInput } from '../screens/defineScreen';
import { BUILD, type LayoutElement, type ScreenUse, type ShowsScreens } from './element';

/** A screen of `screens` shown on its own, without a stack around it, or with options of this show. */
export class ScreenElement<Uses extends ScreenUse = ScreenUse> implements LayoutElement<Uses> {
  constructor(
    private readonly name: string,
    private readonly props?: object,
    private readonly built: Options = {},
    private readonly screenId?: string
  ) {}

  /** The options of this show, over those of the screen's definition. */
  options(options: ScreenOptionsInput<object>): ScreenElement<Uses> {
    const built = mergeOptions(this.built, buildScreenOptions(options, this.props ?? {}));
    return new ScreenElement(this.name, this.props, built, this.screenId);
  }

  /** The id of the screen, to address it. */
  id(id: string): ScreenElement<Uses> {
    return new ScreenElement(this.name, this.props, this.built, id);
  }

  [BUILD](): Layout {
    return {
      component: {
        ...(this.screenId && { id: this.screenId }),
        name: this.name,
        ...(this.props && { passProps: this.props }),
        options: this.built,
      },
    };
  }
}

// The type of an element carries the screens it shows.
export interface ScreenElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}

/**
 * A screen of `screens` in a layout, with its props: `Screen('Details', { id: '42' })`. A screen without props can go
 * by its name alone. The commands that take the layout check the name and the props.
 */
export function Screen<Name extends string>(
  name: Name
): ScreenElement<{ name: Name; props: undefined }>;
export function Screen<Name extends string, const Props extends object>(
  name: Name,
  props: Props
): ScreenElement<{ name: Name; props: Props }>;
export function Screen(name: string, props?: object): ScreenElement {
  return new ScreenElement(name, props);
}
