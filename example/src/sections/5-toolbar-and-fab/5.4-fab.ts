import type { ButtonPressEvent } from '@apexnative/navigation';
import { icons, md } from '../../ui/icons';
import { color, presets } from '../controls';
import { defineScenario } from '../scenario';

// Press handlers that show as their code in the panel.
const pushDetails = Object.assign(
  ({ navigation }: ButtonPressEvent) => navigation.push('Details'),
  { code: "({ navigation }) => navigation.push('Details')" }
);
const showModal = Object.assign(
  ({ navigation }: ButtonPressEvent) => navigation.showModal('Details'),
  { code: "({ navigation }) => navigation.showModal('Details')" }
);

export default defineScenario({
  id: '5.4',
  title: 'Floating action button',
  check:
    'A floating action button over Details (Android, Material). A press sends `buttonPressed` with its `id`; `onPress` gets the navigation of the screen (With onPress pushes Details; Actions with onPress push Details and show it in a modal). `size: mini` is smaller, `alignHorizontally: left` puts it on the left, `hideOnScroll` hides it as the rows scroll down. With `actions` a press opens the buttons of the actions above it. `backgroundColor` fills the button, `clickColor` while it is pressed, `iconColor` colors its icon; `scrimColor` (a color with opacity) dims the screen while the actions show (With actions), and a press on it closes them; `actions[]` sets the icon and the colors of every action; `icon` is an image or the name of a drawable of the app (here Material Symbols).',
  platform: 'android',
  controls: {
    fab: presets(
      {
        Regular: { id: 'fab', icon: icons.plus },
        Mini: { id: 'fab', icon: icons.plus, size: 'mini' },
        Colored: { id: 'fab', icon: icons.plus, backgroundColor: '#00B894', iconColor: '#FFFFFF' },
        'On the left': { id: 'fab', icon: icons.plus, alignHorizontally: 'left' },
        'Hides on scroll': { id: 'fab', icon: icons.plus, hideOnScroll: true },
        'With actions': {
          id: 'fab',
          icon: icons.plus,
          actions: [
            { id: 'fab-photo', icon: icons.star, size: 'mini' },
            { id: 'fab-note', icon: icons.heart, size: 'mini' },
          ],
        },
        'With onPress': { id: 'fab', icon: icons.plus, onPress: pushDetails },
        'Actions with onPress': {
          id: 'fab',
          icon: icons.plus,
          actions: [
            { id: 'fab-push', icon: icons.star, size: 'mini', onPress: pushDetails },
            { id: 'fab-modal', icon: icons.heart, size: 'mini', onPress: showModal },
          ],
        },
        Hidden: { id: 'fab', icon: icons.plus, visible: false },
      },
      { initial: 'Regular' }
    ),
    'fab.icon': presets({
      Plus: icons.plus,
      Star: icons.star,
      'Edit (Material)': md('edit_square'),
      'Share (Material)': md('share'),
      'Add (Material)': md('add'),
    }),
    'fab.backgroundColor': color(),
    'fab.clickColor': color(),
    'fab.iconColor': color(),
    'fab.scrimColor': presets({ Dim: 'rgba(0, 0, 0, 0.32)', Darker: 'rgba(0, 0, 0, 0.6)' }),
    'fab.actions[].icon': presets({
      Star: icons.star,
      Heart: icons.heart,
      'Edit (Material)': md('edit_square'),
      'Share (Material)': md('share'),
    }),
    'fab.actions[].backgroundColor': color(),
    'fab.actions[].iconColor': color(),
  },
  keywords: [
    'fab',
    'floating',
    'actions',
    'hideOnScroll',
    'backgroundColor',
    'iconColor',
    'clickColor',
    'icon',
    'scrimColor',
  ],
});
