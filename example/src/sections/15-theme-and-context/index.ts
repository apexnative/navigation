import type { Section } from '../scenario';
import context from './15.2-context';
import theme from './15.1-theme';
import scheme from './15.3-scheme';

/** Section 15: the theme of the navigation and the app's context. */
export const themeSection: Section = {
  id: 15,
  title: 'Theme and context',
  intro:
    'The look of the whole navigation, changed at once with `setTheme`, and a value every screen reads with `useAppContext`. Each group pushes a screen to try them on.',
  scenarios: [theme, context, scheme],
  runsOnly: true,
};
