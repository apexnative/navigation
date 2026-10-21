import { Platform } from 'react-native';
import { color, oneOf, presets, text } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.1',
  title: 'Title',
  check:
    'The title of the bar: its text and its look. A long text shows how the bar cuts it. `alignment` is Android only: iOS centers the title. `glass` (iOS 26) puts the title on a capsule of Liquid Glass, with the subtitle of 1.2 as a second line; a title component of 1.3 goes on it too.',
  controls: {
    'topBar.title.text': text(),
    'topBar.title.fontSize': oneOf([13, 17, 22, 28]),
    'topBar.title.fontWeight': oneOf(['regular', '600', 'bold', '900']),
    'topBar.title.fontFamily': presets({
      Serif: Platform.select({ ios: 'Georgia', default: 'serif' }),
      Mono: Platform.select({ ios: 'Menlo', default: 'monospace' }),
    }),
    'topBar.title.fontStyle': oneOf(['normal', 'italic']),
    'topBar.title.glass': oneOf(['regular', 'clear', 'none'], { platform: 'ios' }),
    'topBar.title.color': color(),
    'topBar.title.alignment': oneOf(['center', 'fill'], { platform: 'android' }),
  },
  keywords: ['title'],
});
