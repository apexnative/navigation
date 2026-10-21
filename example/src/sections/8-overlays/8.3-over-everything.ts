import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '8.3',
  title: 'Over everything',
  check:
    'An overlay stays over the app while it goes on: show the mini player, then push screens, switch tabs, open a modal — it stays on top and the app works under it. ✕ on it dismisses it; Dismiss all removes every overlay.',
  runs: {
    'Show a mini player': () => navigation.showOverlay('MiniPlayerOverlay'),
    'Dismiss all overlays': () => navigation.dismissAllOverlays(),
  },
  keywords: ['showOverlay', 'dismissAllOverlays', 'touches', 'mini player'],
});
