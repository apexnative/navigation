import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '1.9',
  title: 'Title menu and rename',
  check:
    'A menu on a tap on the title, with a chevron next to it. A pick sends `buttonPressed` with the id of the item: see the Journal. `rename` adds Rename to the menu: the title turns into a field, and the new title comes as `titleRenamed`, also in the Journal. `document` heads the menu with a file: its icon, name and type.',
  platform: 'ios',
  controls: {
    'topBar.title.menu': presets({
      Actions: {
        items: [
          {
            id: 'duplicate',
            title: 'Duplicate',
            icon: { ios: 'plus.square.on.square', android: md('library_add') },
          },
          { id: 'move', title: 'Move', icon: { ios: 'folder', android: md('folder') } },
          {
            id: 'delete',
            title: 'Delete',
            icon: { ios: 'trash', android: md('delete') },
            destructive: true,
          },
        ],
      },
      'Submenu and a choice': {
        items: [
          {
            title: 'Sort by',
            singleSelection: true,
            items: [
              { id: 'name', title: 'Name', checked: true },
              { id: 'date', title: 'Date' },
            ],
          },
          {
            id: 'refresh',
            title: 'Refresh',
            icon: { ios: 'arrow.clockwise', android: md('refresh') },
          },
        ],
      },
    }),
    'topBar.title.rename': oneOf([true, false]),
    'topBar.title.document': presets({
      'Notes.txt': { url: '~/Documents/Notes.txt' },
      'Plan.pdf': { url: '~/Documents/Plan.pdf' },
      Removed: { url: '' },
    }),
  },
  keywords: ['title.menu', 'rename', 'titleRenamed', 'title.document'],
});
