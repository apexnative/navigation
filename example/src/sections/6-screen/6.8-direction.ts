import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.8',
  title: 'Direction of the app',
  check:
    '`navigation.setDirection` turns the whole app around at once: the back button and the title on the right, the tabs from the right, the push from the left. React content follows in the screens shown after the change. Back to left to right when done.',
  runs: {
    'Right to left': () => navigation.setDirection('rtl'),
    'Left to right': () => navigation.setDirection('ltr'),
  },
  keywords: ['direction', 'setDirection', 'rtl'],
});
