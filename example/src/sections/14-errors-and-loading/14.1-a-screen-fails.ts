import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '14.1',
  title: 'A screen fails',
  check:
    'An error in a screen stays in it: the screen shows `errorFallback` of createNavigation with the error and Try again, its top bar stays so back works, and the other screens go on. Fails to start: for 3 seconds its data does not come, so Try again fails again; after that it works. Breaks later: Break it makes the next render throw; Try again renders the screen anew. Each error goes to the Journal as `onError: screen`. A debug build can also show React’s report of the error in LogBox.',
  runs: {
    'Push a screen that fails to start': () =>
      navigation.push('Broken', { props: { downFor: 3000 } }),
    'Push a screen and break it': () => navigation.push('Broken'),
  },
  keywords: ['errorFallback', 'onError', 'retry', 'error boundary'],
});
