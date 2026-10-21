import type { Section } from '../scenario';
import sharedState from './16.1-shared-state';
import theMode from './16.2-the-mode';

/** Section 16: every screen in one React tree. */
export const oneTreeSection: Section = {
  id: 16,
  title: 'One tree',
  intro:
    'Without `appProvider` every screen is a React root of its own, and a provider around one screen does not reach the others. With it every screen renders in one React tree inside the provider: a store, a query client or a theme is shared and lives as long as the app.',
  scenarios: [sharedState, theMode],
  runsOnly: true,
};
