import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.5',
  title: 'Popover',
  check:
    'A popover points at the button it comes from: here the Show button of the toolbar (its id: `sourceButtonId: panel-modal`). On iPhone UIKit shows it as a sheet; `adaptive: false` keeps it a popover. `arrowDirections` are the sides its arrow may point to.',
  platform: 'ios',
  controls: {
    modalPresentationStyle: oneOf(['popover']),
    'modal.popover': presets({
      'From the Show button': { sourceButtonId: 'panel-modal' },
      'From the Show button, kept a popover': { sourceButtonId: 'panel-modal', adaptive: false },
      'Arrow down only': {
        sourceButtonId: 'panel-modal',
        adaptive: false,
        arrowDirections: ['down'],
      },
    }),
  },
  keywords: ['popover', 'sourceButtonId', 'adaptive', 'arrowDirections'],
});
