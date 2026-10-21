import { navigation } from '@apexnative/navigation';
import { log } from '../../showcase/journal';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.3',
  title: 'From anywhere',
  check:
    '`navigation` works outside the screens too — in a timer, a store, a push notification — and acts from the screen the user sees. Push in 3 seconds, then switch to the Journal tab: Details opens there. Log the state puts what the user sees (`getState`) in the Journal.',
  runs: {
    'Push in 3 seconds': () => setTimeout(() => navigation.push('Details'), 3000),
    'Log the state': () => log('state', 'getState', navigation.getState()),
  },
  keywords: ['navigation', 'getState', 'outside', 'timer'],
});
