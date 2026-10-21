import { setNativeFirst } from '../../app/launchMode';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.7',
  title: 'A native first screen',
  check:
    'The app starts with the native screen as its first root, as an app with a native sign-in screen would: its hook runs in a hidden React root that starts with the app, while `willMount` runs. The Journal has how long after the bundle started its props were ready and it showed. Start with it reloads the app; quit the app and open it again for a cold start. Back to the Showcase, on the screen, reloads back.',
  runs: {
    'Start with it': () => setNativeFirst(true),
  },
  keywords: ['useProps', 'initialRoot', 'cold start', 'nativeScreen'],
});
