import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.6',
  title: 'Panes on Android',
  check:
    'Show switches to a split view: in landscape Library beside Details, and a folder picked in Library shows in Details with `showInColumn`; Hide Library and Show Library on Details move it with `hideColumn` and `showColumn`. In portrait the demo shows its `compact` layout, Library, and a pick pushes Details. With `compactTopColumn` set the demo leaves `compact` out: in portrait Details comes over Library, back and its back button take it away, and `secondary` starts with Details. `width` is the width of Library; `presentsWithGesture: false` stops the swipe that moves Details.',
  platform: 'android',
  controls: {
    'splitView.width': oneOf([280, 360, 420]),
    'splitView.compactTopColumn': oneOf(['primary', 'secondary']),
    'splitView.presentsWithGesture': oneOf([true, false]),
  },
  keywords: [
    'splitView',
    'SlidingPaneLayout',
    'showInColumn',
    'showColumn',
    'hideColumn',
    'compact',
  ],
});
