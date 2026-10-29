import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.5',
  title: 'A background the content draws',
  check:
    "Two screens whose content draws a gradient over the screen's own color, the first without a top bar, the second with a transparent one. On Android a push and a pop move the screens 96 dp to the side as the system's transitions do, and the room beside each one goes on with what its edge shows — the gradient, and the orange strip of the left edge or the green strip of the right one: no band of the screen's own color at the sides, and the bar leaves as it showed. iOS slides the whole screen.",
  runs: {
    'Push the screen without a bar': () => navigation.push('Gradient'),
  },
  keywords: ['push', 'pop', 'transition', 'gradient', 'background', 'edge'],
});
