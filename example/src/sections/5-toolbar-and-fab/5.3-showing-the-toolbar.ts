import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '5.3',
  title: 'Showing the toolbar',
  check:
    'The toolbar shows while the screen on top has buttons for it (iOS); `visible: false` hides it with its buttons. On Details switch it while it shows, and push another Details: each screen shows or hides the toolbar of the stack, and the rows go under it.',
  platform: 'ios',
  controls: {
    'toolbar.visible': oneOf([false, true]),
    'toolbar.buttons': presets({
      'Two buttons': [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        { systemItem: 'flexibleSpace' },
        { id: 'done', text: 'Done' },
      ],
    }),
  },
  keywords: ['toolbar', 'visible'],
});
