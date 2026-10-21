import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.5',
  title: 'A function with a result',
  check:
    'The same screen loads its items as it appears: `loadItems` of its props returns a promise, and the native side gets what it resolved with — `try await` in Swift, a `Result` on Android. Load again shows them again; Fail to load calls a function that rejects, and the screen shows the message of the error. The Journal has both.',
  runs: {
    'Push the screen': () => navigation.push('UsePropsDemo'),
    'In a modal': () => navigation.showModal('UsePropsDemo', { closeButton: true }),
  },
  keywords: ['useProps', 'call', 'Promise', 'async', 'Result'],
});
