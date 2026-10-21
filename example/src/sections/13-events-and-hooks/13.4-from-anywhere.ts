import { navigation } from '@apexnative/navigation';
import { log } from '../../showcase/journal';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '13.4',
  title: 'Events outside the screens',
  check:
    '`navigation.on` listens from anywhere — a store, analytics — and returns the function that stops it. For 15 seconds every screen that appears goes to the Journal as `navigation.on`; then it stops. The rest of the Journal comes from `listeners` of createNavigation.',
  runs: {
    'Listen to screens for 15 s': () => {
      const stop = navigation.on('screenDidAppear', (event) =>
        log('event', 'navigation.on', event.screen.name)
      );
      setTimeout(() => {
        stop();
        log('event', 'navigation.on', 'stopped');
      }, 15000);
    },
  },
  keywords: ['navigation.on', 'listeners', 'analytics', 'screenDidAppear'],
});
