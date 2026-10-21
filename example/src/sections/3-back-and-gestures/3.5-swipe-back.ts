import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '3.5',
  title: 'Swipe back',
  check:
    'On iOS a swipe from the left edge goes back, and on iOS 26 a swipe from anywhere on the content too, following the finger. `popGesture: false` turns both off: the back button still works. `edge` keeps the swipe from the edge only: a swipe on the content stays with the content.',
  platform: 'ios',
  controls: {
    popGesture: oneOf([false, true, 'edge']),
  },
  keywords: ['popGesture', 'swipe', 'gesture', 'interactivePop'],
});
