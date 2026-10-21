import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '8.1',
  title: 'A toast',
  check:
    'A strip over the whole app that goes by itself after a moment. Touches outside its views reach the screen under it: scroll the panel while it shows.',
  runs: {
    'Show a toast': () => navigation.showOverlay('ToastOverlay', { props: { text: 'Saved' } }),
  },
  keywords: ['showOverlay', 'overlay', 'toast', 'touches'],
});
