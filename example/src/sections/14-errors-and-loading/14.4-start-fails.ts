import { failNextStart } from '../../app/launchMode';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '14.4',
  title: 'A start that fails',
  check:
    'When `willMount` throws or the first root cannot be built, the app shows `errorFallback` as its first screen in place of the splash screen, and the error goes to `onError` as `start` (the Journal). Fail the next start reloads the app with a `willMount` that throws once: Try again runs the start again from `willMount`, and the Showcase comes.',
  runs: {
    'Fail the next start': () => failNextStart(),
  },
  keywords: ['errorFallback', 'onError', 'willMount', 'start', 'retry'],
});
