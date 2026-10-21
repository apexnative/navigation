import type { Section } from '../scenario';
import appearAndFocus from './13.1-appear-and-focus';
import barButtons from './13.2-bar-buttons';
import tabPresses from './13.3-tab-presses';
import fromAnywhere from './13.4-from-anywhere';

/** Section 13: the events of the navigation and the hooks of a screen. */
export const eventsSection: Section = {
  id: 13,
  title: 'Events and hooks',
  intro:
    'What a screen learns about itself through hooks — appear, focus, its bar buttons, its tab — and what the app learns from anywhere through `navigation.on`. Each group runs with a button; the code of the button is under it, the screens show the hooks they use.',
  scenarios: [appearAndFocus, barButtons, tabPresses, fromAnywhere],
  runsOnly: true,
};
