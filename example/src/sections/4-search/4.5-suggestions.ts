import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '4.5',
  title: 'Suggestions',
  check:
    'A list of suggestions while searching (iOS 16+ and Android): under the field, or a menu when the field is in the bar on iOS. A pick sends `searchSuggestionPicked` with the id of the suggestion (Journal) and keeps the text, and the list goes; typing removes it too.',
  controls: {
    'topBar.searchBar.suggestions': presets({
      Places: [
        {
          id: 'suggest-paris',
          title: 'Paris',
          icon: { ios: 'mappin', android: md('location_on') },
        },
        { id: 'suggest-oslo', title: 'Oslo', icon: { ios: 'mappin', android: md('location_on') } },
        {
          id: 'suggest-lisbon',
          title: 'Lisbon',
          icon: { ios: 'mappin', android: md('location_on') },
        },
      ],
      Removed: [],
    }),
  },
  keywords: ['suggestions', 'searchSuggestionPicked', 'onSuggestion'],
});
