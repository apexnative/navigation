import { color, presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '2.7',
  title: 'The overflow menu',
  check:
    '`overflowMenu` (iOS 16+) puts items of its own in a "•••" menu at the end of the bar ("⋮" on Android); with them the menu button always shows. A pick sends `buttonPressed` with the id of the item. A `pinned` right button never moves into the menu: on iOS it stays past "•••", in a capsule of its own; on Android it stays in the bar while the others move into "⋮" when there is no room.',
  controls: {
    'topBar.overflowMenu': presets({
      'Two items': {
        items: [
          {
            id: 'settings',
            title: 'Settings',
            icon: { ios: 'gearshape', android: md('settings') },
          },
          { id: 'help', title: 'Help', icon: { ios: 'questionmark.circle', android: md('help') } },
        ],
      },
      'Picks and a submenu': {
        singleSelection: true,
        items: [
          { id: 'by-name', title: 'By name', checked: true },
          { id: 'by-date', title: 'By date' },
          {
            title: 'More',
            items: [
              { id: 'archive', title: 'Archive', disabled: true },
              { id: 'delete', title: 'Delete', destructive: true },
            ],
          },
        ],
      },
      Removed: { items: [] },
    }),
    'topBar.rightButtons': presets({
      Pinned: [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        { id: 'done', text: 'Done', style: 'prominent', pinned: true },
      ],
      'Five, one pinned': [
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        { id: 'copy', text: 'Copy', icon: { ios: 'doc.on.doc', android: md('content_copy') } },
        { id: 'flag', text: 'Flag', icon: { ios: 'flag', android: md('flag') } },
        { id: 'favorite', text: 'Favorite', icon: { ios: 'heart', android: md('favorite') } },
        { id: 'done', text: 'Done', style: 'prominent', pinned: true },
      ],
    }),
    'topBar.menuBackgroundColor': color({ platform: 'android' }),
    'topBar.menuTextColor': color({ platform: 'android' }),
  },
  keywords: ['overflowMenu', 'pinned', 'menuBackgroundColor', 'menuTextColor'],
});
