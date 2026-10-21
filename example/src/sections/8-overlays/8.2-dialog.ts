import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '8.2',
  title: 'A dialog',
  check:
    'A dialog over the whole app: the overlay dims everything and takes every touch until a button dismisses it (`dismiss()` from inside). The answer goes to the Journal.',
  runs: {
    'Show a dialog': () => navigation.showOverlay('DialogOverlay'),
  },
  keywords: ['showOverlay', 'dismiss', 'dialog'],
});
