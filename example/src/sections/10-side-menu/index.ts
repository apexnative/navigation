import type { Section } from '../scenario';
import opening from './10.1-opening';
import look from './10.2-look';
import glass from './10.3-glass';

/** Section 10: side menus, shown as a demo app. */
export const sideMenuSection: Section = {
  id: 10,
  title: 'Side menu',
  intro:
    'A layout with a menu on each side of a screen. Show switches the app to a demo with these options: open the menus with a swipe or with the buttons on Home; a pick in a menu goes to the Journal and closes it. Back to the Showcase on Home comes back here.',
  scenarios: [opening, look, glass],
  demo: 'sideMenu',
};
