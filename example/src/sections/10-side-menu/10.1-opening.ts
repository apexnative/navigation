import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '10.1',
  title: 'Opening and size',
  check:
    "Show switches to a demo with a menu on each side; Back to the Showcase on Home comes back. A menu opens with a swipe from its edge (iOS: from anywhere with `entireScreen`) or with the buttons on Home (`openMenu('left')`). A pick in a menu acts on the center and closes the menu: Home starts the center over with `replace`, the others push Details. `enabled: false` turns the swipe off. `width` is the width of the menu.",
  controls: {
    'sideMenu.openGestureMode': oneOf(['entireScreen', 'bezel'], { platform: 'ios' }),
    'sideMenu.left.enabled': oneOf([false, true]),
    'sideMenu.left.width': oneOf([220, 280, 340]),
    'sideMenu.right.enabled': oneOf([false, true]),
    'sideMenu.right.width': oneOf([220, 280, 340]),
  },
  keywords: ['sideMenu', 'openGestureMode', 'enabled', 'width', 'visible'],
});
