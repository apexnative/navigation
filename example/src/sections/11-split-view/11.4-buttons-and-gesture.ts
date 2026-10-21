import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.4',
  title: 'Sidebar button and swipe',
  check:
    '`displayModeButton`: the button in the bar that shows and hides Library — `always`, `never`, or `automatic` (gone when the swipe is off). `presentsWithGesture: false`: a swipe from the edge no longer brings a hidden Library.',
  platform: 'ipad',
  controls: {
    'splitView.displayModeButton': oneOf(['automatic', 'always', 'never']),
    'splitView.presentsWithGesture': oneOf([true, false]),
  },
  keywords: ['displayModeButton', 'presentsWithGesture', 'sidebar button', 'swipe'],
});
