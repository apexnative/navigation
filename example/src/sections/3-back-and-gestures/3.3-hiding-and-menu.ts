import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '3.3',
  title: 'Hiding and the history menu',
  check:
    '`visible: false` hides the back button: check whether the swipe back still goes back. A long press on the back button (iOS) opens the history: push a few Details on Details, then hold it and jump back several screens. `enableMenu: false` gives the screen a back button of its own, without that menu.',
  controls: {
    'topBar.backButton.visible': oneOf([false, true]),
    'topBar.backButton.enableMenu': oneOf([false, true], { platform: 'ios' }),
  },
  keywords: ['backButton', 'visible', 'enableMenu', 'history'],
});
