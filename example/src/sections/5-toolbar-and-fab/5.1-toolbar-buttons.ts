import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '5.1',
  title: 'Toolbar buttons',
  check:
    'The toolbar of the stack at the bottom of Details, with the buttons of the screen on top (iOS). The buttons take the format of the bar buttons; a press goes to the Journal (`buttonPressed`). A disabled one is dimmed; a `menu` opens on a tap; `showText` shows the icon and the text side by side.',
  platform: 'ios',
  controls: {
    'toolbar.buttons': presets({
      Text: [
        { id: 'select', text: 'Select' },
        { systemItem: 'flexibleSpace' },
        { id: 'done', text: 'Done' },
      ],
      Icons: [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        { systemItem: 'flexibleSpace' },
        { id: 'trash', text: 'Delete', icon: { ios: 'trash', android: md('delete') } },
      ],
      Prominent: [
        { systemItem: 'flexibleSpace' },
        { id: 'send', text: 'Send', style: 'prominent' },
      ],
      'With a menu': [
        {
          id: 'sort',
          text: 'Sort',
          icon: { ios: 'arrow.up.arrow.down', android: md('swap_vert') },
          menu: {
            singleSelection: true,
            items: [
              { id: 'sort-name', title: 'Name', checked: true },
              { id: 'sort-date', title: 'Date' },
            ],
          },
        },
        { systemItem: 'flexibleSpace' },
        {
          id: 'compose',
          text: 'Compose',
          icon: { ios: 'square.and.pencil', android: md('edit_square') },
        },
      ],
      'Icon and text': [
        {
          id: 'filter',
          text: 'Filter',
          icon: { ios: 'line.3.horizontal.decrease', android: md('filter_list') },
          showText: true,
        },
        { systemItem: 'flexibleSpace' },
        {
          id: 'compose',
          text: 'Compose',
          icon: { ios: 'square.and.pencil', android: md('edit_square') },
          showText: true,
        },
      ],
      Disabled: [
        { id: 'archive', text: 'Archive', enabled: false },
        { systemItem: 'flexibleSpace' },
        { id: 'done', text: 'Done' },
      ],
      None: [],
    }),
  },
  keywords: ['toolbar', 'buttons', 'buttonPressed'],
});
