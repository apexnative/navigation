import { icons, md } from '../../ui/icons';
import { presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '2.1',
  title: 'Kinds of buttons',
  check:
    'A button is a text, an SF Symbol (iOS), an image or a system item. A press sends `buttonPressed` with its `id`: see the Journal. A disabled one is dimmed and sends nothing. `showText` (iOS) shows the icon and the text side by side; with a `group` it shares a capsule with an icon. Left buttons on a pushed screen take the place of the back button.',
  controls: {
    'topBar.rightButtons': presets({
      Text: [{ id: 'save', text: 'Save' }],
      'SF Symbol': [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
      ],
      Image: [{ id: 'star', text: 'Star', icon: icons.star }],
      'System item': [{ id: 'add', systemItem: 'add' }],
      'Two buttons': [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        { id: 'heart', text: 'Like', icon: { ios: 'heart', android: md('favorite') } },
      ],
      Disabled: [{ id: 'save', text: 'Save', enabled: false }],
      'Icon and text': [
        {
          id: 'share',
          text: 'Share',
          icon: { ios: 'square.and.arrow.up', android: md('share') },
          showText: true,
        },
      ],
      'Icon and text, with an icon': [
        {
          id: 'share',
          text: 'Share',
          icon: { ios: 'square.and.arrow.up', android: md('share') },
          showText: true,
          group: 'share',
        },
        {
          id: 'heart',
          text: 'Like',
          icon: { ios: 'heart', android: md('favorite') },
          group: 'share',
        },
      ],
      None: [],
    }),
    'topBar.leftButtons': presets({
      Text: [{ id: 'cancel', text: 'Cancel' }],
      'SF Symbol': [
        { id: 'menu', text: 'Menu', icon: { ios: 'line.3.horizontal', android: md('menu') } },
      ],
      None: [],
    }),
  },
  keywords: [
    'rightButtons',
    'leftButtons',
    'SF Symbol',
    'icon',
    'systemItem',
    'enabled',
    'buttonPressed',
  ],
});
