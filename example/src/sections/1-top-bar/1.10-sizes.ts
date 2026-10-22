import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.10',
  title: 'Sizes and margins',
  check:
    'The sizes of the bar on Android: `height` and `topMargin` of the bar, `height` and `topMargin` of the title (the buttons stay in place). A fraction (56.5, 12.5) stays a fraction until the size is in pixels: the bar is as tall as 56.5 points on iOS, not 56.',
  platform: 'android',
  controls: {
    'topBar.height': oneOf([44, 56, 56.5, 96]),
    'topBar.topMargin': oneOf([0, 24]),
    'topBar.title.height': oneOf([24, 40]),
    'topBar.title.topMargin': oneOf([0, 12, 12.5]),
  },
  keywords: ['height', 'topMargin'],
});
