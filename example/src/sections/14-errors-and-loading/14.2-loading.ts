import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '14.2',
  title: 'A screen loads',
  check:
    'A screen that waits for its data (React’s `use` with a promise, Suspense) shows `loadingFallback` of createNavigation for 2 seconds; its top bar is there at once. Load again waits once more, with the fallback in place of the content.',
  runs: {
    'Push a screen that loads its data': () => navigation.push('Slow'),
  },
  keywords: ['loadingFallback', 'Suspense', 'use', 'lazy', 'loading'],
});
