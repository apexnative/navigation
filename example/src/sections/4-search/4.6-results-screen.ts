import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '4.6',
  title: 'A results screen',
  check:
    'A search screen of its own over Details (`resultsComponent`, here `SearchResults`): recent places while the field is empty, then the places that match as you type. `resultsActivation`: as soon as the search opens, or once you type. With `hideTopBarOnFocus` (4.2) the field moves to the top; on Android the search opens over the whole screen, the field at the top.',
  controls: {
    'topBar.searchBar.resultsComponent': presets({ SearchResults: { name: 'SearchResults' } }),
    'topBar.searchBar.resultsActivation': oneOf(['onSearchActivation', 'onTextEntry']),
  },
  keywords: ['resultsComponent', 'resultsActivation'],
});
