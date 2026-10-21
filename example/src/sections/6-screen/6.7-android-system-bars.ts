import { color, oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.7',
  title: 'System bars and the keyboard',
  check:
    'The navigation bar of Android: its color, hidden, the screen drawn behind it. `adjustResize` shrinks the screen above the keyboard (tap the text field on Details).',
  platform: 'android',
  controls: {
    'navigationBar.backgroundColor': color(),
    'navigationBar.visible': oneOf([false, true]),
    'navigationBar.drawBehind': oneOf([true, false]),
    'layout.adjustResize': oneOf([true, false]),
  },
  keywords: ['navigationBar', 'adjustResize'],
});
