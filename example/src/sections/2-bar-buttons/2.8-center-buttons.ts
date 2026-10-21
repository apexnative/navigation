import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '2.8',
  title: 'Center buttons',
  check:
    '`centerButtons` (iOS 16+) sit in the middle of the bar on iPad with `style: browser` or `editor`; with `navigator`, and on iPhone, they go to the "•••" menu. With a `customizationId` the user arranges them: long press on the bar, then Customize Toolbar.',
  platform: 'ipad',
  controls: {
    'topBar.style': oneOf(['browser', 'editor', 'navigator']),
    'topBar.centerButtons': presets({
      'Three tools': [
        { id: 'pen', text: 'Pen', icon: { ios: 'pencil.tip', android: md('draw') } },
        {
          id: 'shapes',
          text: 'Shapes',
          icon: { ios: 'square.on.circle', android: md('category') },
        },
        { id: 'text', text: 'Text', icon: { ios: 'textformat', android: md('text_format') } },
      ],
      None: [],
    }),
    'topBar.customizationId': oneOf(['showcase-tools']),
  },
  keywords: ['centerButtons', 'customizationId', 'style', 'iPad'],
});
