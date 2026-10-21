import { DevSettings } from 'react-native';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '19.4',
  title: 'Launch',
  check:
    'What runs as the app starts: `willMount` before the first root, while the splash screen shows (an app puts what it loads into the context with `navigation.setContext`), then the root of `initialRoot`, then `didMount`. Reload and look at the Journal: willMount, then didMount.',
  runs: {
    'Reload the app': () => DevSettings.reload(),
  },
  keywords: ['willMount', 'didMount', 'initialRoot', 'launch', 'splash'],
});
