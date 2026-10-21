import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '4.3',
  title: 'Scopes',
  check:
    'Segments that narrow the search, under the field (or at the top when the field is at the bottom); on Android filter chips. A pick goes to the Journal with `searchChanged` as `scope`. `selectedScope` picks one from the options. `scopeBarActivation` (iOS 16+ and Android): when they show — as soon as the search opens, or once you type; `automatic` on Android shows them as it opens.',
  controls: {
    'topBar.searchBar.scopes': presets({
      'All, Places, People': [
        { id: 'all', title: 'All' },
        { id: 'places', title: 'Places' },
        { id: 'people', title: 'People' },
      ],
      Removed: [],
    }),
    'topBar.searchBar.selectedScope': oneOf(['all', 'places', 'people']),
    'topBar.searchBar.scopeBarActivation': oneOf([
      'automatic',
      'onTextEntry',
      'onSearchActivation',
    ]),
  },
  keywords: ['scopes', 'selectedScope', 'scopeBarActivation'],
});
