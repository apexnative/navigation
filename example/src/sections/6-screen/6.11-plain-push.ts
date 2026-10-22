import { navigation } from '@apexnative/navigation';
import { showcaseTheme } from '../../app/theme';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.11',
  title: 'A plain push',
  check:
    "An app's first push as it is written, under a theme without a background: Home is a red `View` with a `Text` and a button, Details a centered `Text` with no background. Details takes the system background, so Home never shows through it; the push runs smoothly and Details' text is there as the push ends.",
  runs: {
    'Push Home': () => {
      navigation.setTheme({ colors: { ...showcaseTheme.colors, background: undefined } });
      navigation.push('PlainHome');
    },
  },
  keywords: ['backgroundColor', 'push', 'default background'],
});
