import { color, oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.6',
  title: 'Border and the look at the top',
  check:
    'The line under the bar: `noBorder` hides it (iOS), `borderColor` colors it, `borderHeight` and `elevation` are Android. `scrollEdgeAppearance` (iOS) is the look of the bar while the content is at its top — its background, border and title: scroll the rows on Details and the bar takes its usual look. It needs `active: true`.',
  controls: {
    'topBar.noBorder': oneOf([true, false], { platform: 'ios' }),
    'topBar.borderColor': color(),
    'topBar.borderHeight': oneOf([1, 3, 6], { platform: 'android' }),
    'topBar.elevation': oneOf([0, 4, 12], { platform: 'android' }),
    'topBar.scrollEdgeAppearance.active': oneOf([true, false], { platform: 'ios' }),
    'topBar.scrollEdgeAppearance.background.color': color({ platform: 'ios' }),
    'topBar.scrollEdgeAppearance.background.translucent': oneOf([true, false], { platform: 'ios' }),
    'topBar.scrollEdgeAppearance.noBorder': oneOf([true, false], { platform: 'ios' }),
    'topBar.scrollEdgeAppearance.borderColor': color({ platform: 'ios' }),
    'topBar.scrollEdgeAppearance.title': presets(
      {
        Bigger: { fontSize: 20, fontWeight: '800' },
        Colored: { color: '#6C5CE7' },
        'Serif italic': { fontFamily: 'Georgia', fontStyle: 'italic' },
      },
      { platform: 'ios' }
    ),
  },
  keywords: ['noBorder', 'borderColor', 'borderHeight', 'elevation', 'scrollEdgeAppearance'],
});
