import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '13.2',
  title: 'Bar buttons in hooks',
  check:
    'Buttons that follow the state of the screen. Like: `onPress` in `useScreenOptions`, the title and the text of the button count the presses. Refresh: a button without a handler of its own; `useNavigationButton` takes its presses by id (also in the Journal).',
  runs: {
    'Push the buttons screen': () => navigation.push('ButtonHooks'),
  },
  keywords: ['useScreenOptions', 'useNavigationButton', 'onPress', 'buttons'],
});
