import { color } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '2.10',
  title: 'Colors of the buttons',
  check:
    'The color of all left, right and center buttons of the bar, and of the disabled ones. Pick buttons in 2.1 (Disabled for the disabled color) and 2.8 for the center ones (iPad). A `color` of a button wins over these.',
  controls: {
    'topBar.leftButtonColor': color(),
    'topBar.rightButtonColor': color(),
    'topBar.leftButtonDisabledColor': color(),
    'topBar.rightButtonDisabledColor': color(),
    'topBar.centerButtonColor': color({ platform: 'ios' }),
    'topBar.centerButtonDisabledColor': color({ platform: 'ios' }),
  },
  keywords: ['leftButtonColor', 'rightButtonColor', 'disabledColor', 'centerButtonColor'],
});
