import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '4.7',
  title: 'In the toolbar',
  check:
    "On iOS 26 the search field can sit in the toolbar between its buttons: `{ systemItem: 'searchField' }` in `toolbar.buttons`, whatever `placement` says (turn `visible` on in 4.1). Without the field the toolbar buttons leave no room, and the search is a magnifier in the bar.",
  platform: 'ios',
  controls: {
    'toolbar.buttons': presets({
      'Field between buttons': [
        {
          id: 'filter',
          text: 'Filter',
          icon: { ios: 'line.3.horizontal.decrease', android: md('filter_list') },
        },
        { systemItem: 'searchField' },
        {
          id: 'compose',
          text: 'Compose',
          icon: { ios: 'square.and.pencil', android: md('edit_square') },
        },
      ],
      'Buttons, no field': [
        {
          id: 'filter',
          text: 'Filter',
          icon: { ios: 'line.3.horizontal.decrease', android: md('filter_list') },
        },
        { systemItem: 'flexibleSpace' },
        {
          id: 'compose',
          text: 'Compose',
          icon: { ios: 'square.and.pencil', android: md('edit_square') },
        },
      ],
    }),
  },
  keywords: ['toolbar', 'searchField', 'placement'],
});
