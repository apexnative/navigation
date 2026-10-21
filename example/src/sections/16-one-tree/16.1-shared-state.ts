import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '16.1',
  title: 'One store for every screen',
  check:
    'With `appProvider` every screen renders in one React tree inside it: one store serves them all. Save an item, push another screen and open one in a modal: all of them show the same count, the title in the bar too, and a save in the modal shows under it. In the other mode (`screenProvider`) each screen has a store of its own and starts from 0. The screen says which mode the Showcase runs in.',
  runs: {
    'Push the shared state screen': () => navigation.push('SharedState'),
  },
  keywords: ['appProvider', 'screenProvider', 'one tree', 'slots', 'store', 'provider'],
});
