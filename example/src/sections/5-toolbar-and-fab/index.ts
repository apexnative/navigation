import type { Section } from '../scenario';
import toolbarButtons from './5.1-toolbar-buttons';
import spacesAndCapsules from './5.2-spaces-and-capsules';
import showingTheToolbar from './5.3-showing-the-toolbar';
import fab from './5.4-fab';

/** Section 5: the toolbar of a stack (iOS) and the floating action button (Android). */
export const toolbarAndFabSection: Section = {
  id: 5,
  title: 'Toolbar and FAB',
  intro:
    'What a screen puts at the bottom: the toolbar of the stack with its buttons on iOS, and the floating action button on Android. Presses go to the Journal.',
  scenarios: [toolbarButtons, spacesAndCapsules, showingTheToolbar, fab],
};
