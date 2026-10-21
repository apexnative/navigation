import { presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '2.9',
  title: 'Component buttons',
  check:
    'A React component as a button: `component` names a component of `screens` (here `BarButtonView`, a counter). It handles its own touches: a tap counts and goes to the Journal, the bar sends no `buttonPressed`. On iOS 26 it has no glass; `hideSharedBackground: false` puts it on glass.',
  controls: {
    'topBar.rightButtons': presets({
      Component: [{ id: 'like', component: { name: 'BarButtonView' } }],
      'With props': [
        { id: 'like', component: { name: 'BarButtonView', passProps: { label: 'Clap' } } },
      ],
      'On glass': [
        { id: 'like', component: { name: 'BarButtonView' }, hideSharedBackground: false },
      ],
    }),
  },
  keywords: ['component', 'passProps'],
});
