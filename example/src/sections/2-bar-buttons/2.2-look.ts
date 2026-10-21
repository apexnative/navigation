import { icons, md } from '../../ui/icons';
import { presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '2.2',
  title: 'Look of a button',
  check:
    '`prominent` (iOS) is the main action: a capsule in the button color, the system draws its title. `color` colors a button, the font options its text. `hideSharedBackground` (iOS 26) takes the glass off. `disableIconTint` draws an image as it is. `iconBackground` puts a shape of its own behind an icon.',
  controls: {
    'topBar.rightButtons': presets({
      Prominent: [{ id: 'done', text: 'Done', style: 'prominent' }],
      'Prominent, colored': [{ id: 'send', text: 'Send', style: 'prominent', color: '#00B894' }],
      'Colored text': [{ id: 'save', text: 'Save', color: '#E17055' }],
      Font: [{ id: 'save', text: 'Save', fontSize: 19, fontWeight: '800', fontFamily: 'Georgia' }],
      'No glass': [
        {
          id: 'share',
          text: 'Share',
          icon: { ios: 'square.and.arrow.up', android: md('share') },
          hideSharedBackground: true,
        },
      ],
      'Image as is': [{ id: 'star', text: 'Star', icon: icons.star, disableIconTint: true }],
      'Icon background': [
        {
          id: 'plus',
          text: 'Add',
          icon: icons.plus,
          color: '#FFFFFF',
          iconBackground: { color: '#6C5CE7', cornerRadius: 16, width: 32, height: 32 },
        },
      ],
    }),
  },
  keywords: [
    'style',
    'prominent',
    'color',
    'fontSize',
    'hideSharedBackground',
    'disableIconTint',
    'iconBackground',
  ],
});
