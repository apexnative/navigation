import type { Section } from '../scenario';
import searchBar from './4.1-search-bar';
import whileSearching from './4.2-while-searching';
import scopes from './4.3-scopes';
import tokens from './4.4-tokens';
import suggestions from './4.5-suggestions';
import resultsScreen from './4.6-results-screen';
import inTheToolbar from './4.7-in-the-toolbar';

/** Section 4: the search bar of a screen. */
export const searchSection: Section = {
  id: 4,
  title: 'Search',
  intro:
    'The search bar of a screen: where it sits, how it looks while searching, scopes, tokens (iOS), suggestions, a results screen of its own and the field in the toolbar (iOS). Turn the search bar on in 4.1 first. Its events go to the Journal.',
  scenarios: [searchBar, whileSearching, scopes, tokens, suggestions, resultsScreen, inTheToolbar],
};
