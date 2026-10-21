import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '4.4',
  title: 'Tokens',
  check:
    'Tokens before the text of the field: a filter the user sees and can delete (backspace at the start of the field). `searchChanged` in the Journal carries the tokens the field has. Closing the search clears them, as UIKit does.',
  platform: 'ios',
  controls: {
    'topBar.searchBar.tokens': presets({
      'One token': [
        { id: 'favorites', text: 'Favorites', icon: { ios: 'star', android: md('star') } },
      ],
      'Two tokens': [
        { id: 'favorites', text: 'Favorites', icon: { ios: 'star', android: md('star') } },
        { id: 'recent', text: 'This week', icon: { ios: 'clock', android: md('schedule') } },
      ],
      Removed: [],
    }),
  },
  keywords: ['tokens', 'searchChanged'],
});
