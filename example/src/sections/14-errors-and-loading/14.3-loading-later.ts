import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '14.3',
  title: 'A screen loads its code later',
  check:
    'The screen is `{ load: () => import(…) }` in `screens`: its module runs the first time a push shows it (the Journal gets “Later: module loaded” then, not at the start), and the push waits for it, so the title from the module’s `defineScreen` is there from the first frame. A second push shows the same load time: the module ran once.',
  runs: {
    'Push a screen loaded later': () => navigation.push('Later', { props: { from: '14.3' } }),
  },
  keywords: ['load', 'import', 'lazy', 'code splitting', 'loading'],
});
