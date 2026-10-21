import { oneOf, text } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '4.1',
  title: 'The search bar',
  check:
    '`visible: true` gives Details a search bar; the other groups of this section need it. `placement` (iOS 16+): `stacked` under the title; on iPhone with iOS 26 `automatic` and `integrated` put the field at the bottom of the screen. On Android `stacked` and `automatic` are a field under the title, every `integrated` a magnifier in the bar; the field opens in the bar. `hideOnScroll` hides it as the rows scroll (on Android with the bar). `focus: true` opens the search with the keyboard. Typing goes to the Journal (`searchChanged`), the Search key as `searchSubmitted`, closing as `searchCancelled`.',
  controls: {
    'topBar.searchBar.visible': oneOf([true, false]),
    'topBar.searchBar.placeholder': text(),
    'topBar.searchBar.placement': oneOf([
      'stacked',
      'automatic',
      'integrated',
      'integratedButton',
      'integratedCentered',
    ]),
    'topBar.searchBar.hideOnScroll': oneOf([true, false]),
    'topBar.searchBar.focus': oneOf([true, false]),
  },
  keywords: ['searchBar', 'placement', 'hideOnScroll', 'focus', 'searchChanged'],
});
