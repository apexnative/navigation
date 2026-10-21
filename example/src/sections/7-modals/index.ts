import type { Section } from '../scenario';
import presentation from './7.1-presentation';
import sheet from './7.2-sheet';
import dismissing from './7.3-dismissing';
import formSheetSize from './7.4-form-sheet-size';
import popover from './7.5-popover';

/** Section 7: modals — how they show, sheets, dismissing, form sheets on iPad and popovers. */
export const modalsSection: Section = {
  id: 7,
  title: 'Modals',
  intro:
    'A screen shown as a modal: its presentation style and transition, the sheet and its detents, how it goes away, the size of a form sheet on iPad, and a popover. Show opens Details as a modal with them. Its events go to the Journal.',
  scenarios: [presentation, sheet, dismissing, formSheetSize, popover],
  modalOnly: true,
};
