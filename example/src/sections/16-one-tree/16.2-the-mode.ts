import { setOneTree } from '../../app/launchMode';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '16.2',
  title: 'The mode of the Showcase',
  check:
    'The mode is set at launch: `appProvider` (one tree) or `screenProvider` (a root per screen) in createNavigation. These buttons keep the mode (on iOS in the settings of the app, on Android in its preferences) and reload the app, in a release build too on Android. In one tree every section works the same way and can be checked again in it.',
  runs: {
    'One React tree (reloads)': () => setOneTree(true),
    'A root per screen (reloads)': () => setOneTree(false),
  },
  keywords: ['appProvider', 'createNavigation', 'reload'],
});
