import type { Section } from '../scenario';
import title from './1.1-title';
import subtitle from './1.2-subtitle';
import titleComponent from './1.3-title-component';
import largeTitle from './1.4-large-title';
import background from './1.5-background';
import borderAndScrollEdge from './1.6-border-and-scroll-edge';
import visibility from './1.7-visibility';
import barLayout from './1.8-bar-layout';
import titleMenu from './1.9-title-menu';
import sizes from './1.10-sizes';

/** Section 1: the top bar of a screen in a stack. */
export const topBarSection: Section = {
  id: 1,
  title: 'Top bar',
  intro:
    'Everything a screen in a stack sets for its top bar. Open a group, pick the values (— leaves an option unset), and press Push or Modal: Details shows with them. On Details the same groups change the options while it shows.',
  scenarios: [
    title,
    subtitle,
    titleComponent,
    largeTitle,
    background,
    borderAndScrollEdge,
    visibility,
    barLayout,
    titleMenu,
    sizes,
  ],
};
