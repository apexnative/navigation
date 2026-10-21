import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '15.2',
  title: 'The app’s context',
  check:
    'A value for every screen without a provider: `useAppContext` reads it, `navigation.setContext` changes it, and every screen that reads it renders again; `useAppContext((context) => context.user)` renders only when the user changes. Sign in: the title in the bar greets the user. Open it in a modal, sign out there and close it: the screen under it changed too. The first value is `context` of createNavigation.',
  runs: {
    'Push the context screen': () => navigation.push('ContextDemo'),
  },
  keywords: ['useAppContext', 'setContext', 'context', 'willMount'],
});
