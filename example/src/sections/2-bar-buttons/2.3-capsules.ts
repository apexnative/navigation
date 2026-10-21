import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '2.3',
  title: 'Capsules',
  check:
    'On iOS 26 buttons sit on glass capsules. Neighbors with the same `group` share one capsule, also with a text button; without a group each button has its own.',
  platform: 'ios',
  controls: {
    'topBar.rightButtons': presets({
      'One capsule': [
        {
          id: 'bold',
          text: 'Bold',
          icon: { ios: 'bold', android: md('format_bold') },
          group: 'format',
        },
        {
          id: 'italic',
          text: 'Italic',
          icon: { ios: 'italic', android: md('format_italic') },
          group: 'format',
        },
        {
          id: 'underline',
          text: 'Underline',
          icon: { ios: 'underline', android: md('format_underlined') },
          group: 'format',
        },
      ],
      'Two groups': [
        {
          id: 'undo',
          text: 'Undo',
          icon: { ios: 'arrow.uturn.backward', android: md('undo') },
          group: 'history',
        },
        {
          id: 'redo',
          text: 'Redo',
          icon: { ios: 'arrow.uturn.forward', android: md('redo') },
          group: 'history',
        },
        {
          id: 'share',
          text: 'Share',
          icon: { ios: 'square.and.arrow.up', android: md('share') },
          group: 'more',
        },
        {
          id: 'heart',
          text: 'Like',
          icon: { ios: 'heart', android: md('favorite') },
          group: 'more',
        },
      ],
      'Text in a group': [
        { id: 'edit', text: 'Edit', group: 'edit' },
        {
          id: 'share',
          text: 'Share',
          icon: { ios: 'square.and.arrow.up', android: md('share') },
          group: 'edit',
        },
      ],
      'Each its own': [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        { id: 'heart', text: 'Like', icon: { ios: 'heart', android: md('favorite') } },
      ],
    }),
  },
  keywords: ['group', 'capsule', 'glass'],
});
