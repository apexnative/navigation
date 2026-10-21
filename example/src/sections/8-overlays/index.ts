import type { Section } from '../scenario';
import toast from './8.1-toast';
import dialog from './8.2-dialog';
import overEverything from './8.3-over-everything';
import keyboard from './8.4-keyboard';

/** Section 8: overlays — components shown over the whole app. */
export const overlaysSection: Section = {
  id: 8,
  title: 'Overlays',
  intro:
    'A component shown over the whole app, above the screens, tabs and modals: a toast, a dialog, a mini player, a form. Each group shows its overlay with a button; the code of the button is under it.',
  scenarios: [toast, dialog, overEverything, keyboard],
  runsOnly: true,
};
