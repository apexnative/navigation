import type { Section } from '../scenario';
import aScreenFails from './14.1-a-screen-fails';
import loading from './14.2-loading';
import loadingLater from './14.3-loading-later';
import startFails from './14.4-start-fails';

/** Section 14: what a screen shows when it fails or waits. */
export const errorsSection: Section = {
  id: 14,
  title: 'Errors and loading',
  intro:
    'What a screen shows when its render fails (`errorFallback`, `onError`) and while it waits for data (`loadingFallback`) or for its code (`load`), and what a start that fails shows. The Showcase sets both in createNavigation for all its screens; each group pushes a screen that fails or waits, or starts the app again with a start that fails.',
  scenarios: [aScreenFails, loading, loadingLater, startFails],
  runsOnly: true,
};
