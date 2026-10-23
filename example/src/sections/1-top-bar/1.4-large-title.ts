import { Platform } from 'react-native';
import { color, oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.4',
  title: 'Large title',
  check:
    '`displayMode`: `always` on this screen, `never` not, `automatic` as on the screen below, `inline` small in the row of the bar (iOS 26) — only with no back button: next to one UIKit shows it as `always`, so open it with Modal. Scroll the rows on Details: the title collapses into the bar and comes back at the top; with `collapseOnScroll: false` it stays. Android: the large top app bar of Material 3, the title fades into the row as it collapses. On both, with Push, Back and the swipe back (and opening any section from the Showcase home), the large title moves in with its screen from the first frame of the transition, never only after it.',
  controls: {
    'topBar.largeTitle.visible': oneOf([true, false]),
    'topBar.largeTitle.displayMode': oneOf(
      Platform.OS === 'ios'
        ? ['always', 'never', 'automatic', 'inline']
        : ['always', 'never', 'automatic']
    ),
    'topBar.largeTitle.collapseOnScroll': oneOf([false, true]),
    'topBar.largeTitle.fontSize': oneOf([28, 34, 40]),
    'topBar.largeTitle.fontWeight': oneOf(['regular', 'bold', '900']),
    'topBar.largeTitle.fontStyle': oneOf(['normal', 'italic']),
    'topBar.largeTitle.fontFamily': presets({
      Serif: Platform.select({ ios: 'Georgia', default: 'serif' }),
    }),
    'topBar.largeTitle.color': color(),
  },
  keywords: ['largeTitle', 'displayMode', 'collapseOnScroll'],
});
