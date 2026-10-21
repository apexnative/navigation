import { navigation } from '@apexnative/navigation';
import { log } from '../../showcase/journal';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.1',
  title: 'Push and pop',
  check:
    'push shows a screen over this panel; Back on it (the button, a swipe, or `pop`) comes back, and the Journal gets `screenPopped`. A push takes props and options of this show, over those of the screen; `animations.push.enabled: false` shows it at once. push resolves with the id of the screen it showed.',
  runs: {
    'Push Details': () => navigation.push('Details'),
    'Push with props': () => navigation.push('StackStep', { props: { step: 1 } }),
    'Push with options of this show': () =>
      navigation.push('Details', { options: (s) => s.title('Opened with options') }),
    'Push without animation': () =>
      navigation.push('Details', { options: { animations: { push: { enabled: false } } } }),
    'Push and log the id': () =>
      navigation.push('Details').then((id) => log('command', 'push resolved', id)),
  },
  keywords: ['push', 'pop', 'props', 'animations', 'screenPopped'],
});
