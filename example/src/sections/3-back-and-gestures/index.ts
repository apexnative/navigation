import type { Section } from '../scenario';
import title from './3.1-title';
import look from './3.2-look';
import hidingAndMenu from './3.3-hiding-and-menu';
import intercept from './3.4-intercept';
import swipeBack from './3.5-swipe-back';
import hardwareBack from './3.6-hardware-back';

/** Section 3: going back — the back button, the swipe and the system back of Android. */
export const backSection: Section = {
  id: 3,
  title: 'Back and gestures',
  intro:
    'How a screen pushed on a stack goes back: the back button and its look, its history menu, taking over the press, the swipe back, and the system back of Android. Details shows with the options; push another Details on it to have more screens behind.',
  scenarios: [title, look, hidingAndMenu, intercept, swipeBack, hardwareBack],
};
