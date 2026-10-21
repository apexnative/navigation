import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '2.11',
  title: 'Changing buttons',
  check:
    'On Details change the buttons in 2.1 while it shows: with `animateRightButtons` / `animateLeftButtons` on, the new buttons fade in (iOS); off, they change at once. On iOS 26 buttons that share a capsule (Icon and text, with an icon) change at once either way: UIKit has no animation for groups of buttons.',
  platform: 'ios',
  controls: {
    'topBar.animateRightButtons': oneOf([true, false]),
    'topBar.animateLeftButtons': oneOf([true, false]),
  },
  keywords: ['animateRightButtons', 'animateLeftButtons'],
});
