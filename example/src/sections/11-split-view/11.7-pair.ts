import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.7',
  title: 'A pair of screens',
  check:
    'Two screens as one screen of the stack, unlike the columns of a split view: Profile (coral, the primary) and Details (teal, the secondary). The pair on any window (`defineArrangement` with `compact: pair`) shows both everywhere: one above the other on a phone or iPhone Duo folded, side by side when open, each on its side of the fold when partly folded. Profile with `arrangement: { secondary: Details }` is a plain screen on a phone and on iPhone Duo folded, and comes with Details on iPad, iPhone Duo open, an Android tablet or an open foldable. The overlay puts Profile over Details, side by side when partly folded. With `fraction: 1/3` Profile takes a third of a split, and half at a fold. The trailing pair makes Details the primary with `primaryEdge: trailing`: Profile on the leading side, Details on the trailing side with 60% of the pair (`fraction: 0.6`; half at a fold), at the bottom one above the other, the bar Details’; outside a large window (a phone, iPhone Duo folded) Details shows alone, and opening or folding iPhone Duo keeps Details in place, with no jump. Pair settings picks the primary (the screen alone when folded, the bar), `primaryEdge`, `style` and `compact`, and shows that pair. Each screen shows `useArrangement`; the bar is the primary’s (Profile’s, Details’ in the trailing pair); back takes both. Before iOS 27.1 only Profile shows.',
  runs: {
    'Pair settings: choose the primary, its side, compact': () => navigation.push('PairSettings'),
    'Push the pair on any window': () => navigation.push('PairEverywhere', { props: { id: '9' } }),
    'Push Profile: a pair in a large window only': () =>
      navigation.push('PairProfile', { props: { id: '7' } }),
    'Push the overlay pair': () => navigation.push('PairOverlay', { props: { id: '8' } }),
    'Push a pair with a third for Profile': () =>
      navigation.push('PairThird', { props: { id: '6' } }),
    'Push the trailing pair: Details leads, alone when folded': () =>
      navigation.push('PairDetailsFirst', { props: { id: '5' } }),
  },
  keywords: [
    'defineArrangement',
    'arrangement',
    'useArrangement',
    'iPhone Duo',
    'foldable',
    'pair',
    'primaryEdge',
  ],
});
