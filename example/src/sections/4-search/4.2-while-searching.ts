import { color, oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '4.2',
  title: 'While searching',
  check:
    'Open the search (a tap on the field). `hideTopBarOnFocus` hides the bar while searching (iOS; on Android the open field always takes the bar), `obscuresBackgroundDuringPresentation` dims the screen under it until you type; a tap there closes the search. `backgroundColor` colors the field, `tintColor` the cursor and the selection.',
  controls: {
    'topBar.searchBar.hideTopBarOnFocus': oneOf([true, false]),
    'topBar.searchBar.obscuresBackgroundDuringPresentation': oneOf([true, false]),
    'topBar.searchBar.backgroundColor': color(),
    'topBar.searchBar.tintColor': color(),
  },
  keywords: [
    'hideTopBarOnFocus',
    'obscuresBackgroundDuringPresentation',
    'backgroundColor',
    'tintColor',
  ],
});
