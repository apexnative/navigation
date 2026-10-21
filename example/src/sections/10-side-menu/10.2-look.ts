import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '10.2',
  title: 'How it opens',
  check:
    'How the left menu comes in (iOS). `openMode`: `aboveContent` (the default) slides the menu in over the center, which dims; `pushContent` moves the center aside and the menu is under it. `animationType` is how a menu under the center appears — `parallax`, `slide`, `slide-and-scale`, `door` — so it needs `pushContent`. `shouldStretchDrawer`: drag the open menu further out and it stretches up to a tenth wider; with `false` it stops at its width. `animationVelocity`: the speed of the menu in points a second (840 by default), seen best with the buttons on Home; a fast swipe goes at the speed of the finger.',
  platform: 'ios',
  controls: {
    'sideMenu.left.openMode': oneOf(['pushContent', 'aboveContent']),
    'sideMenu.animationType': oneOf(['parallax', 'slide', 'slide-and-scale', 'door']),
    'sideMenu.left.shouldStretchDrawer': oneOf([true, false]),
    'sideMenu.left.animationVelocity': oneOf([300, 840, 3000]),
  },
  keywords: [
    'animationType',
    'openMode',
    'aboveContent',
    'pushContent',
    'shouldStretchDrawer',
    'animationVelocity',
  ],
});
