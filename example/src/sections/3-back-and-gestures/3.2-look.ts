import { icons, md } from '../../ui/icons';
import { color, oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '3.2',
  title: 'Look of the back button',
  check:
    '`color` colors the arrow and the title; the font options change the title (iOS: give it a `title` in 3.1). `icon` replaces the arrow: an image, or `{ ios, android }` — an SF Symbol on iOS, a Material Symbol on Android; `iconBackground` puts a shape behind an icon. On iOS 26 the back button is on glass; `hideSharedBackground` takes the glass off.',
  controls: {
    'topBar.backButton.color': color(),
    'topBar.backButton.fontSize': oneOf([13, 17, 20]),
    'topBar.backButton.fontWeight': oneOf(['regular', '600', 'bold']),
    'topBar.backButton.fontFamily': presets({ Serif: 'Georgia' }, { platform: 'ios' }),
    'topBar.backButton.icon': presets({
      Close: icons.close,
      'Down chevron': { ios: 'chevron.down', android: md('expand_more') },
      Cross: { ios: 'xmark', android: md('close') },
      'Left arrow': { ios: 'arrow.left', android: md('arrow_back') },
    }),
    'topBar.backButton.iconBackground': presets({
      'Purple circle': { color: '#6C5CE7', cornerRadius: 16, width: 32, height: 32 },
    }),
    'topBar.backButton.hideSharedBackground': oneOf([true, false], { platform: 'ios' }),
  },
  keywords: ['backButton', 'color', 'icon', 'SF Symbol', 'iconBackground', 'hideSharedBackground'],
});
