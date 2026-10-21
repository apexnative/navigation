import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.7',
  title: 'Hiding the bar',
  check:
    '`visible: false` hides the bar; on Details switch it with `animate` on and off. `hideOnScroll` hides it while the rows scroll down and brings it back as they scroll up; the screen goes under the bar by itself, and the rows show where it was (on Android the list starts its rows below the bar with `useSafeAreaInsets().top`). `hideOnTap` (iOS): a tap on the screen. `hideOnKeyboard` (iOS): tap the text field on Details. `drawBehind` puts the content under the bar.',
  controls: {
    'topBar.visible': oneOf([false, true]),
    'topBar.animate': oneOf([true, false]),
    'topBar.hideOnScroll': oneOf([true, false]),
    'topBar.drawBehind': oneOf([true, false]),
    'topBar.hideOnTap': oneOf([true, false], { platform: 'ios' }),
    'topBar.hideOnKeyboard': oneOf([true, false], { platform: 'ios' }),
  },
  keywords: ['visible', 'animate', 'hideOnScroll', 'hideOnTap', 'hideOnKeyboard', 'drawBehind'],
});
