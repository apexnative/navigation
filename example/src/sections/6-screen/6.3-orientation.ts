import { presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.3',
  title: 'Orientation',
  check:
    'The orientations the screen allows; the visible screen decides. The Showcase is portrait: Details with `Landscape` turns the phone as it shows (iOS 16+) and back on the way back. With `All` it follows the device. On iPad an app with multitasking keeps its own orientations.',
  controls: {
    'layout.orientation': presets({
      Portrait: ['portrait'],
      Landscape: ['landscape'],
      All: ['all'],
    }),
  },
  keywords: ['orientation', 'landscape', 'portrait', 'rotate'],
});
