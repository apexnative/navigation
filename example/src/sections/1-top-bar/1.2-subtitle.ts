import { Platform } from 'react-native';
import { color, oneOf, presets, text } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.2',
  title: 'Subtitle',
  check:
    'A second line under the title. On iOS it follows the title: centered under it, or at the leading edge under a large title (turn `largeTitle.visible` on in 1.4). `alignment: center` is Android only.',
  controls: {
    'topBar.subtitle.text': text(),
    'topBar.subtitle.fontSize': oneOf([11, 13, 15]),
    'topBar.subtitle.fontWeight': oneOf(['regular', '600', 'bold']),
    'topBar.subtitle.fontStyle': oneOf(['normal', 'italic']),
    'topBar.subtitle.fontFamily': presets({
      Serif: Platform.select({ ios: 'Georgia', default: 'serif' }),
    }),
    'topBar.subtitle.color': color(),
    'topBar.subtitle.alignment': oneOf(['center'], { platform: 'android' }),
  },
  keywords: ['subtitle'],
});
