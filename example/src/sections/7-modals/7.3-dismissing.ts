import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.3',
  title: 'Dismissing',
  check:
    'A sheet goes down with a swipe. The draft keeps it while its field has text (`usePreventLeave`): the swipe down and ✕ ask “Discard the draft?”, and the try goes to the Journal as `modalDismissAttempted`. Discard closes it with `goBack()`; every way out goes to the Journal as `modalDismissed`.',
  platform: 'ios',
  runs: {
    'A draft in a sheet': () => navigation.showModal('Draft', { closeButton: true }),
  },
  keywords: ['usePreventLeave', 'dismiss', 'modalDismissed', 'modalDismissAttempted'],
});
