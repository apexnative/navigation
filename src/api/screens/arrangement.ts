import type { EngineArrangement } from '../../engine/screens';

const ARRANGEMENT = Symbol('apexnative.navigation.arrangement');

/**
 * A screen with a second screen beside or under it in a large window (iPad, iPhone Duo open, Android tablets and open
 * foldables): `arrangement` of `defineScreen` and `nativeScreen`. Anywhere else (any phone, iPhone Duo folded, iOS
 * before 27.1) the screen shows alone, as a plain screen.
 */
export interface ScreenArrangement<Props extends object = {}> {
  /**
   * The screen beside or under this one: a name of `screens`. What belongs to the whole window is the primary's: the
   * secondary's `topBar`, `toolbar`, `statusBar` and `layout.orientation` have no effect (iOS: also the home indicator,
   * the deferred system gestures and `layout.verticalBars`; Android: also `navigationBar`).
   */
  secondary: string;
  /** The props of the secondary from those of the show; without it the same props. */
  secondaryProps?: (props: Props) => object;
  /**
   * `split`: side by side, each on its side of the fold of a partly folded device. `overlay`: this screen over the
   * secondary, both full size; side by side on a partly folded device.
   * @default 'split'
   */
  style?: 'split' | 'overlay';
}

/** A pair under a name of its own, with every setting: `defineArrangement`. */
export interface ArrangementDefinition<Props extends object = {}> extends ScreenArrangement<Props> {
  /** The screen that owns the bar: its title, buttons, toolbar and status bar are the pair's. A name of `screens`. */
  primary: string;
  /** The axes the pair may use. Where the window allows none of them, only the primary shows. @default 'both' */
  axes?: 'horizontal' | 'vertical' | 'both';
  /** `split`: the primary's share along the split, above 0 and below 1, where the fold does not decide. @default 0.5 */
  fraction?: number;
  /** `overlay`: the edge the primary takes when the pair goes side by side. @default 'trailing' */
  primaryEdge?: 'leading' | 'trailing' | 'top' | 'bottom';
  /**
   * Outside a large window: `primary` — the primary alone, as a plain screen; `pair` — the pair as the system lays it
   * out (one above the other on a phone held upright).
   * @default 'primary'
   */
  compact?: 'primary' | 'pair';
}

/** An entry of `screens` made by `defineArrangement`. */
export interface Arrangement<Props extends object = {}> {
  readonly [ARRANGEMENT]: true;
  readonly definition: ArrangementDefinition<Props>;
  /** Carries the type of the props; never set. */
  readonly props?: Props;
}

/**
 * Two screens of `screens` shown as one screen of a stack under a name of their own, with every setting of the pair.
 * Commands, layouts and links name it as a screen. For a screen that always comes with the same second screen,
 * `arrangement` of `defineScreen` is simpler.
 *
 * @example
 * const screens = {
 *   Profile: ProfileScreen,
 *   Details: DetailsScreen,
 *   ProfileDetails: defineArrangement<{ id: string }>({
 *     primary: 'Profile',
 *     secondary: 'Details',
 *     style: 'overlay',
 *   }),
 * };
 * navigation.push('ProfileDetails', { props: { id: '7' } });
 */
export function defineArrangement<Props extends object = {}>(
  definition: ArrangementDefinition<Props>
): Arrangement<Props> {
  checkFraction(definition.fraction);
  return { [ARRANGEMENT]: true, definition };
}

export function isArrangement(value: unknown): value is Arrangement<any> {
  return typeof value === 'object' && value !== null && ARRANGEMENT in value;
}

/** The pair as the engine keeps it: the settings `arrangement` of a screen has not, at their defaults. */
export function toEngineArrangement(
  primary: string,
  arrangement: ScreenArrangement<any> | ArrangementDefinition<any>
): EngineArrangement {
  const full = arrangement as ArrangementDefinition<any>;
  checkFraction(full.fraction);
  return {
    primary,
    secondary: arrangement.secondary,
    secondaryProps: arrangement.secondaryProps,
    style: arrangement.style ?? 'split',
    axes: full.axes ?? 'both',
    fraction: full.fraction ?? 0.5,
    primaryEdge: full.primaryEdge ?? 'trailing',
    compact: full.compact ?? 'primary',
  };
}

function checkFraction(fraction: number | undefined) {
  if (fraction !== undefined && !(fraction > 0 && fraction < 1)) {
    throw new Error(
      `[navigation] arrangement: fraction is ${fraction}, it goes above 0 and below 1`
    );
  }
}
