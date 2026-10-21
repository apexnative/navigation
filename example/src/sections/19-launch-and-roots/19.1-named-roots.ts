import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '19.1',
  title: 'Roots by name',
  check:
    'The roots of an app by name, in `roots` of createNavigation: the Showcase, `login` (signed out) and `welcome`, a root made from a param (the name of the user). switchTo replaces the whole app with one: sign in on Login, sign out on Welcome, Back to the Showcase on either. The names and the params are typed.',
  runs: {
    'Switch to login': () => navigation.switchTo('login'),
    'Switch to welcome with a name': () => navigation.switchTo('welcome', 'Sam'),
  },
  keywords: ['roots', 'switchTo', 'initialRoot', 'login', 'param'],
});
