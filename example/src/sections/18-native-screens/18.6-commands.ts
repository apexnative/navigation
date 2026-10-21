import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.6',
  title: 'Commands from a native screen',
  check:
    'The native screen runs commands of the navigation from itself, as `useNavigation()` of a React screen does: Push Details pushes a React screen over it, Show Details as a modal shows one, Close pops it, or dismisses its modal when it is the first screen of one.',
  runs: {
    'Push the screen': () => navigation.push('UsePropsDemo'),
    'In a modal': () => navigation.showModal('UsePropsDemo', { closeButton: true }),
  },
  keywords: ['navigate', 'push', 'showModal', 'pop', 'dismiss', 'nativeScreen'],
});
