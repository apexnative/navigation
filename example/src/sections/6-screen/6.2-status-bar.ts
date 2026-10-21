import { color, oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.2',
  title: 'Status bar',
  check:
    '`visible: false` hides the status bar, `style` gives it light or dark text. On iOS `animate` fades the change, `hideWithTopBar` hides it with the top bar (`topBar.visible: false` here), `blur` puts a blur under it. On Android `backgroundColor`, `drawBehind` and `translucent` set what is under it: `drawBehind: false` puts the top bar below the status bar, which keeps a strip of its own (`backgroundColor`, or the window); without a top bar `drawBehind` puts the screen under the status bar, which shows with `safeAreaEdges` of 6.1 without the top; `translucent` lays a dark translucent layer on the status bar, darker than the bar under it.',
  controls: {
    'statusBar.visible': oneOf([false, true]),
    'statusBar.style': oneOf(['light', 'dark']),
    'statusBar.animate': oneOf([true, false], { platform: 'ios' }),
    'statusBar.hideWithTopBar': oneOf([true, false], { platform: 'ios' }),
    'statusBar.blur': oneOf([true, false], { platform: 'ios' }),
    'topBar.visible': oneOf([false, true]),
    'statusBar.backgroundColor': color({ platform: 'android' }),
    'statusBar.drawBehind': oneOf([true, false], { platform: 'android' }),
    'statusBar.translucent': oneOf([true, false], { platform: 'android' }),
  },
  keywords: ['statusBar', 'style', 'hideWithTopBar', 'blur'],
});
