import type { Layout, LayoutStackChildren } from '../../interfaces/Layout';
import type { Options } from '../../interfaces/Options';
import {
  BUILD,
  type LayoutElement,
  screenComponent,
  type ScreenUse,
  type ShowsScreens,
  type UsesOf,
} from './element';
import { ScreenElement } from './Screen';

/** A screen of a stack: by name, or `Screen(…)` with its props and the options of this show. */
export type StackEntry = string | ScreenElement<any>;

/**
 * A stack of screens; the last one shows, and back goes to the one before. The options of screens go on the screens
 * (`defineScreen`, `Screen(…).options()`), or on many at once through `groups` and `presentation`.
 */
export class StackElement<Uses extends ScreenUse = ScreenUse> implements LayoutElement<Uses> {
  // No command takes the id of a stack: the commands of a stack act from one of its screens.
  constructor(
    private readonly entries: readonly StackEntry[],
    private readonly built: Options = {}
  ) {}

  [BUILD](): Layout {
    return {
      stack: {
        children: this.entries.map(stackChild),
        options: this.built,
      },
    };
  }
}

export function stackChild(entry: StackEntry): LayoutStackChildren {
  return entry instanceof ScreenElement ? entry[BUILD]() : screenComponent(entry);
}

// The type of an element carries the screens it shows.
export interface StackElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}

export function Stack<const Entries extends readonly StackEntry[]>(
  ...entries: Entries
): StackElement<UsesOf<Entries[number]>> {
  return new StackElement(entries);
}
