import type { Section } from '../scenario';
import kinds from './2.1-kinds';
import look from './2.2-look';
import capsules from './2.3-capsules';
import badges from './2.4-badges';
import toggles from './2.5-toggles';
import menus from './2.6-menus';
import overflow from './2.7-overflow';
import centerButtons from './2.8-center-buttons';
import component from './2.9-component';
import colors from './2.10-colors';
import animate from './2.11-animate';
import verticalBars from './2.12-vertical-bars';

/** Section 2: the buttons of the top bar. */
export const barButtonsSection: Section = {
  id: 2,
  title: 'Bar buttons',
  intro:
    'The buttons of the top bar: kinds, look, capsules, badges, toggles, menus, "•••", the middle of the bar (iPad), components and colors. Most groups set the right buttons: a pick in one group replaces the buttons of another. Presses go to the Journal.',
  scenarios: [
    kinds,
    look,
    capsules,
    badges,
    toggles,
    menus,
    overflow,
    centerButtons,
    component,
    colors,
    animate,
    verticalBars,
  ],
};
