import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '2.5',
  title: 'Toggles',
  check:
    'A `toggle` switches on and off at each tap (iOS): on iOS 26 it is filled when on, and `buttonPressed` carries the new state. `selected` is the state it starts with; on Details set it to switch the button from code.',
  platform: 'ios',
  controls: {
    'topBar.rightButtons': presets({
      Toggle: [
        {
          id: 'favorite',
          text: 'Favorite',
          icon: { ios: 'star', android: md('star') },
          toggle: true,
        },
      ],
      'On at the start': [
        {
          id: 'favorite',
          text: 'Favorite',
          icon: { ios: 'star', android: md('star') },
          toggle: true,
          selected: true,
        },
      ],
      'Shown on, no toggle': [
        {
          id: 'filter',
          text: 'Filter',
          icon: { ios: 'line.3.horizontal.decrease', android: md('filter_list') },
          selected: true,
        },
      ],
    }),
  },
  keywords: ['toggle', 'selected'],
});
