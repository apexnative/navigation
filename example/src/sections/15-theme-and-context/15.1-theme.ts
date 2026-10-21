import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '15.1',
  title: 'Theme',
  check:
    'setTheme changes the default options of every screen at once, the ones on screen too: the buttons and the back button, titles, backgrounds, the selected tab and badges. Ocean also has a `bar` color: on iOS 26 the bars turn solid instead of glass. Forest has none, so they stay glass. React content follows through `useNavigationTheme`: the preview on the theme screen. The Showcase theme brings it all back. The Showcase sets its own look as `theme` of createNavigation.',
  runs: {
    'Push the theme screen': () => navigation.push('ThemeDemo'),
  },
  keywords: ['setTheme', 'useNavigationTheme', 'theme', 'colors', 'dark mode'],
});
