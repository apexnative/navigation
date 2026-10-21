import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.7',
  title: 'A pair of screens',
  check:
    'Two screens as one screen of the stack, unlike the columns of a split view: Profile (coral, the primary) and Details (teal, the secondary). The pair on any window (`defineArrangement` with `compact: pair`) shows both everywhere: one above the other on a phone or iPhone Duo folded, side by side when open, each on its side of the fold when partly folded. Profile with `arrangement: { secondary: Details }` is a plain screen on a phone and on iPhone Duo folded, and comes with Details on iPad, iPhone Duo open, an Android tablet or an open foldable. The overlay puts Profile over Details, side by side when partly folded. With `fraction: 1/3` Profile takes a third of a split, and half at a fold. Each screen shows `useArrangement`; the bar is Profile’s; back takes both. Before iOS 27.1 only Profile shows.',
  runs: {
    'Push the pair on any window': () => navigation.push('PairEverywhere', { props: { id: '9' } }),
    'Push Profile: a pair in a large window only': () =>
      navigation.push('PairProfile', { props: { id: '7' } }),
    'Push the overlay pair': () => navigation.push('PairOverlay', { props: { id: '8' } }),
    'Push a pair with a third for Profile': () =>
      navigation.push('PairThird', { props: { id: '6' } }),
  },
  keywords: [
    'defineArrangement',
    'arrangement',
    'useArrangement',
    'iPhone Duo',
    'foldable',
    'pair',
  ],
});
