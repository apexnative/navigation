import { navigation, Root, Screen, Stack } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

// A modal, a sheet over it, and 2 seconds later a new root: the modals close with the old root.
async function overModals(switchRoot: () => unknown) {
  await navigation.showModal('Details', { closeButton: true });
  await navigation.showModal('Details', { sheet: { detents: ['medium', 'large'] } });
  setTimeout(switchRoot, 2000);
}

export default defineScenario({
  id: '19.6',
  title: 'A new root over modals',
  check:
    'A new root while modals show, as a sign out from a sheet: Details comes as a modal, a sheet of Details over it, and 2 seconds later the new root replaces the app. The modals go with the old root: the new root comes with its transition and nothing of the old root, its modals and its sheet stays over it or comes back. Back on the new root returns to the Showcase without them.',
  runs: {
    'Switch: the platform': () =>
      overModals(() => navigation.switchTo(Root(Stack(Screen('RootDemo'))))),
    'Switch: none': () =>
      overModals(() => navigation.switchTo(Root(Stack(Screen('RootDemo'))).transition('none'))),
    'Switch: slideUp': () =>
      overModals(() => navigation.switchTo(Root(Stack(Screen('RootDemo'))).transition('slideUp'))),
  },
  keywords: ['switchTo', 'Root', 'setRoot', 'modal', 'sheet', 'sign out'],
});
