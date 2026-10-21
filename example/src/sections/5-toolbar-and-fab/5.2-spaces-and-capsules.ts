import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '5.2',
  title: 'Spaces and capsules',
  check:
    'How the toolbar lays its buttons out (iOS). `flexibleSpace` takes the free width and pushes the next buttons to the far side. On iOS 26 icons next to each other share one glass capsule; `fixedSpace` starts a new one. A text button always has a capsule of its own there.',
  platform: 'ios',
  controls: {
    'toolbar.buttons': presets({
      'Icons in one capsule': [
        { id: 'bold', text: 'Bold', icon: { ios: 'bold', android: md('format_bold') } },
        { id: 'italic', text: 'Italic', icon: { ios: 'italic', android: md('format_italic') } },
        {
          id: 'underline',
          text: 'Underline',
          icon: { ios: 'underline', android: md('format_underlined') },
        },
      ],
      'Split by fixedSpace': [
        { id: 'bold', text: 'Bold', icon: { ios: 'bold', android: md('format_bold') } },
        { id: 'italic', text: 'Italic', icon: { ios: 'italic', android: md('format_italic') } },
        { systemItem: 'fixedSpace' },
        {
          id: 'list',
          text: 'List',
          icon: { ios: 'list.bullet', android: md('format_list_bulleted') },
        },
      ],
      'Both sides': [
        { id: 'undo', text: 'Undo', icon: { ios: 'arrow.uturn.backward', android: md('undo') } },
        { id: 'redo', text: 'Redo', icon: { ios: 'arrow.uturn.forward', android: md('redo') } },
        { systemItem: 'flexibleSpace' },
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
      ],
      'Centered with spaces': [
        { systemItem: 'flexibleSpace' },
        {
          id: 'record',
          text: 'Record',
          icon: { ios: 'record.circle', android: md('radio_button_checked') },
        },
        { systemItem: 'flexibleSpace' },
      ],
    }),
  },
  keywords: ['toolbar', 'flexibleSpace', 'fixedSpace', 'capsule'],
});
