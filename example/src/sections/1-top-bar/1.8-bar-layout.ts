import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.8',
  title: 'Layout of the bar',
  check:
    '`style` lays the bar out (iOS 16+): `navigator` centers the title, `browser` and `editor` put it next to the back button. On iOS 26 `browser` and `editor` look the same.',
  platform: 'ios',
  controls: {
    'topBar.style': oneOf(['navigator', 'browser', 'editor']),
  },
  keywords: ['style', 'navigator', 'browser', 'editor'],
});
