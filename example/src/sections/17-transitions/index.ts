import type { Section } from '../scenario';
import contentBackground from './17.5-content-background';
import custom from './17.1-custom';
import roots from './17.4-roots';
import sharedElements from './17.3-shared-elements';
import zoomAndPreview from './17.2-zoom-and-preview';

/** Section 17: transitions and animations. */
export const transitionsSection: Section = {
  id: 17,
  title: 'Transitions and animations',
  intro:
    'How screens come and go: animations of push, pop and modals, the zoom of iOS 18 and a preview on a long press, an element that moves from one screen to the next, and a new root with a transition. Each group runs with a button; the code is under it.',
  scenarios: [custom, zoomAndPreview, sharedElements, roots, contentBackground],
  runsOnly: true,
};
