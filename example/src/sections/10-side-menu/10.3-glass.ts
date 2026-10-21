import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '10.3',
  title: 'A menu on glass',
  check:
    'On iOS 26 `glass` puts the menu on Liquid Glass; the menu screen has no background of its own then (the demo sets it transparent). Over the center (`openMode: aboveContent` in 10.2) the center shows through the glass.',
  platform: 'ios',
  controls: {
    'sideMenu.left.glass': oneOf([true, false]),
    'sideMenu.right.glass': oneOf([true, false]),
  },
  keywords: ['glass', 'Liquid Glass', 'sideMenu'],
});
