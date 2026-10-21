import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '8.4',
  title: 'Keyboard in an overlay',
  check:
    'A text field in an overlay gets the keyboard as one on a screen does, with no setting: tap the field and type, Done dismisses it.',
  runs: {
    'Show a form': () => navigation.showOverlay('FormOverlay'),
  },
  keywords: ['showOverlay', 'keyboard', 'text field'],
});
