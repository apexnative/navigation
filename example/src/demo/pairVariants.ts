import { type Arrangement, defineArrangement } from '@apexnative/navigation';

/** The screen that leads a pair of the settings screen: its bar, alone outside a large window. */
export type PairLead = 'Profile' | 'Details';
export type PairStyle = 'split' | 'overlay';
export type PairEdge = 'leading' | 'trailing' | 'top' | 'bottom';
export type PairCompact = 'primary' | 'pair';

export const PAIR_LEADS: PairLead[] = ['Profile', 'Details'];
export const PAIR_STYLES: PairStyle[] = ['split', 'overlay'];
export const PAIR_EDGES: PairEdge[] = ['leading', 'trailing', 'top', 'bottom'];
export const PAIR_COMPACTS: PairCompact[] = ['primary', 'pair'];

type Cap<Word extends string> = Capitalize<Word>;

/** The name of the entry of `screens` with these settings. */
export type PairVariantName =
  `PairOf${PairLead}${Cap<PairStyle>}${Cap<PairEdge>}${Cap<PairCompact>}`;

const cap = <Word extends string>(word: Word) =>
  (word.charAt(0).toUpperCase() + word.slice(1)) as Cap<Word>;

export function pairVariantName(
  lead: PairLead,
  style: PairStyle,
  edge: PairEdge,
  compact: PairCompact
): PairVariantName {
  return `PairOf${lead}${cap(style)}${cap(edge)}${cap(compact)}`;
}

/**
 * Every pair of Profile and Details the settings screen of 11.7 can show: an app writes the one it needs, as
 * `defineArrangement({ primary: 'Details', secondary: 'Profile', primaryEdge: 'trailing' })`. The settings of a pair
 * are fixed when the screens are registered, so each combination is an entry of its own.
 */
export const pairVariants = {} as Record<PairVariantName, Arrangement<{ id: string }>>;

for (const lead of PAIR_LEADS) {
  for (const style of PAIR_STYLES) {
    for (const edge of PAIR_EDGES) {
      for (const compact of PAIR_COMPACTS) {
        pairVariants[pairVariantName(lead, style, edge, compact)] = defineArrangement<{
          id: string;
        }>({
          primary: lead === 'Profile' ? 'PairProfile' : 'PairDetails',
          secondary: lead === 'Profile' ? 'PairDetails' : 'PairProfile',
          style,
          primaryEdge: edge,
          compact,
        });
      }
    }
  }
}
