import { color, presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

const actions = [
  { id: 'copy', title: 'Copy', icon: { ios: 'doc.on.doc', android: md('content_copy') } },
  { id: 'move', title: 'Move', icon: { ios: 'folder', android: md('folder') } },
  {
    id: 'delete',
    title: 'Delete',
    icon: { ios: 'trash', android: md('delete') },
    destructive: true,
  },
];

export default defineScenario({
  id: '2.6',
  title: 'Menus',
  check:
    "A `menu` opens on a tap: the system menu on iOS, Material's popup menu under the button on Android; a pick sends `buttonPressed` with the id of the item. With `menuOnLongPress` a tap is the button press and a long press opens the menu. A `singleSelection` menu keeps its check on the picked item. Android leaves out the title of the menu; `menuBackgroundColor` and `menuTextColor` paint its menus.",
  controls: {
    'topBar.rightButtons': presets({
      'Menu on tap': [
        {
          id: 'more',
          text: 'More',
          icon: { ios: 'ellipsis', android: md('more_horiz') },
          menu: { items: actions },
        },
      ],
      'Menu on long press': [
        {
          id: 'add',
          text: 'Add',
          icon: { ios: 'plus', android: md('add') },
          menuOnLongPress: true,
          menu: { items: actions },
        },
      ],
      'Single selection': [
        {
          id: 'sort',
          text: 'Sort',
          icon: { ios: 'arrow.up.arrow.down', android: md('swap_vert') },
          menu: {
            title: 'Sort by',
            singleSelection: true,
            items: [
              { id: 'sort-name', title: 'Name', checked: true },
              { id: 'sort-date', title: 'Date' },
              { id: 'sort-size', title: 'Size' },
            ],
          },
        },
      ],
      Submenu: [
        {
          id: 'more',
          text: 'More',
          icon: { ios: 'ellipsis', android: md('more_horiz') },
          menu: {
            items: [
              {
                title: 'Share',
                icon: { ios: 'square.and.arrow.up', android: md('share') },
                items: actions.slice(0, 2),
              },
              {
                id: 'delete',
                title: 'Delete',
                icon: { ios: 'trash', android: md('delete') },
                destructive: true,
              },
            ],
          },
        },
      ],
    }),
    'topBar.menuBackgroundColor': color({ platform: 'android' }),
    'topBar.menuTextColor': color({ platform: 'android' }),
  },
  keywords: ['menu', 'menuOnLongPress', 'singleSelection', 'menuBackgroundColor', 'menuTextColor'],
});
