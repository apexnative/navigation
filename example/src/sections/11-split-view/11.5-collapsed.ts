import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.5',
  title: 'Collapsed: iPhone and a narrow window',
  check:
    'On iPhone and in a narrow iPad window the columns collapse into one stack. The demo gives the split view a `compact` layout for it: Library; there `showInColumn` pushes Details. With `compactTopColumn` set the demo leaves `compact` out and UIKit collapses the columns itself: `primary` puts Library on top, `secondary` Details; a pick in Library puts Details on top, and back goes to Library (with three columns: Library, the items, Details).',
  platform: 'ios',
  controls: {
    'splitView.compactTopColumn': oneOf(['primary', 'secondary']),
  },
  keywords: ['compact', 'compactTopColumn', 'collapsed', 'iPhone'],
});
