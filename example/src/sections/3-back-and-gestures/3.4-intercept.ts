import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '3.4',
  title: 'Keeping the user on a screen',
  check:
    'The draft has `usePreventLeave(text.length > 0, …)`: while its field has text, the back button, and on iOS the swipe from the edge and the history menu, ask “Discard the draft?” instead of leaving — the try goes to the Journal. Discard leaves with `goBack()`; Clear the field, and back leaves at once.',
  runs: {
    'Push a draft': () => navigation.push('Draft'),
  },
  keywords: ['usePreventLeave', 'back', 'swipe', 'unsaved', 'draft'],
});
