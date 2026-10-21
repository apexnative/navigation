import { Linking } from 'react-native';
import { navigation } from '@apexnative/navigation';
import { log } from '../../showcase/journal';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '19.5',
  title: 'Deep links',
  check:
    'The routes of `linking` in `app/App.ts`. A cold start by a link: quit the Showcase, then `xcrun simctl openurl booted apexnative://details` or `adb shell am start -W -a android.intent.action.VIEW -d "apexnative://details" dev.apexnative.navigation.showcase` — the splash goes and Details is already up over the Showcase (`start`), with no transition; Dismiss shows the Showcase under it. In the running app the same link pushes Details (`open`). `welcome/Sam` has only `start`: in the running app it switches to the welcome root. `login` is a screen name: it pushes Login. `saved` waits while nobody signed in: Sign in as Sam opens it by itself, after the context changed. Without a signed-in user, Show the waiting link, then Open or Forget it. A link no route takes goes to the Journal.',
  runs: {
    'apexnative://details': () => Linking.openURL('apexnative://details'),
    'apexnative://welcome/Sam': () => Linking.openURL('apexnative://welcome/Sam'),
    'apexnative://login': () => Linking.openURL('apexnative://login'),
    'apexnative://saved': () => Linking.openURL('apexnative://saved'),
    'apexnative://nowhere': () => Linking.openURL('apexnative://nowhere'),
    'Sign in as Sam': () => navigation.setContext({ user: 'Sam' }),
    'Sign out': () => navigation.setContext({ user: null }),
    'Show the waiting link': () =>
      log('state', 'Waiting link', navigation.getPendingLink()?.url ?? 'none'),
    'Open the waiting link': () => navigation.openPendingLink(),
    'Forget the waiting link': () => navigation.clearPendingLink(),
  },
  keywords: ['linking', 'deep link', 'URL', 'start', 'open', 'when', 'pending', 'usePendingLink'],
});
