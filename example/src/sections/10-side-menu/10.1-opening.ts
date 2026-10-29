import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '10.1',
  title: 'Opening and size',
  check:
    "Show switches to a demo with a menu on each side; Back to the Showcase on Home comes back. A menu opens with a swipe from its edge (iOS: from anywhere with `entireScreen`) or with the buttons on Home (`openMenu('left')`). A pick in a menu acts on the center and closes the menu: the menu starts to close at the tap, and the center changes as it goes. Home starts the center over with `replace`, the others push Details, whose slide is seen. `enabled: false` turns the swipe off. `width` is the width of the menu. `menusAttachMode` is when a menu's screen is created: its header shows the time, and the Journal logs it. `together` and `afterCenter` create both with Show, `onOpen` each at its first open (a swipe or a button), so its time is that of the open and it may show its background for a moment; a second open keeps the time. In one tree (`appProvider`) the screens render at once in every mode, so the time is that of Show.",
  controls: {
    'sideMenu.openGestureMode': oneOf(['entireScreen', 'bezel'], { platform: 'ios' }),
    'sideMenu.left.enabled': oneOf([false, true]),
    'sideMenu.left.width': oneOf([220, 280, 340]),
    'sideMenu.right.enabled': oneOf([false, true]),
    'sideMenu.right.width': oneOf([220, 280, 340]),
    'sideMenu.menusAttachMode': oneOf(['onOpen', 'afterCenter', 'together']),
  },
  keywords: ['sideMenu', 'openGestureMode', 'enabled', 'width', 'visible', 'menusAttachMode'],
});
