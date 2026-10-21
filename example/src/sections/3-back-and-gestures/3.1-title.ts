import { oneOf, text } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '3.1',
  title: 'Title of the back button',
  check:
    'The back button of Details (iOS). On iOS 26 it is the arrow alone unless it has a `title` or `showTitle: true` (the title of the screen below). `displayMode` is how it shortens when the bar is full: `default` the title, then "Back", then the arrow; `generic` "Back"; `minimal` the arrow alone.',
  platform: 'ios',
  controls: {
    'topBar.backButton.title': text(),
    'topBar.backButton.showTitle': oneOf([true, false]),
    'topBar.backButton.displayMode': oneOf(['default', 'generic', 'minimal']),
  },
  keywords: ['backButton', 'title', 'showTitle', 'displayMode'],
});
