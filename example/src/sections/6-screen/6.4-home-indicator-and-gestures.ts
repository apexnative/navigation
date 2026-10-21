import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.4',
  title: 'Home indicator and system gestures',
  check:
    '`autoHideHomeIndicator` fades the home indicator out while the screen is not touched. `deferSystemGestures`: on those edges the first swipe goes to the app and the system shows a hint; a second swipe does the system gesture (home, Control Center). For games, drawing, video.',
  platform: 'ios',
  controls: {
    'layout.autoHideHomeIndicator': oneOf([true, false]),
    'layout.deferSystemGestures': presets({
      Bottom: ['bottom'],
      'Top and bottom': ['top', 'bottom'],
      None: [],
    }),
  },
  keywords: ['autoHideHomeIndicator', 'deferSystemGestures', 'home indicator'],
});
