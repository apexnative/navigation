import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.4',
  title: 'Size of a form sheet',
  check:
    'On iPad a `formSheet` takes `contentSize`: a width and a height, or the height of its content. On iPhone a form sheet is a page sheet.',
  platform: 'ipad',
  controls: {
    modalPresentationStyle: oneOf(['formSheet']),
    'modal.contentSize': presets({
      '540 × 400': { width: 540, height: 400 },
      '400 × its content': { width: 400, height: 'content' },
    }),
  },
  keywords: ['formSheet', 'contentSize'],
});
