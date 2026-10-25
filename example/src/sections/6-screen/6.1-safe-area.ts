import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.1',
  title: 'Safe area',
  check:
    'Where the content of the screen starts and ends. Without `safeAreaEdges` the screen keeps the top and the sides, but those under a bar that shows, and goes down to the bottom: under the home indicator or the navigation bar the list keeps its last rows clear itself with `useSafeAreaInsets()`, as it does under a bar. Listed edges are kept exactly as given: with All four the bottom keeps the content above the home indicator or the navigation bar (the gesture handle, 24 dp on this phone, or the three buttons); `[]` lets the content go everywhere. On Android the top keeps the content under the status bar, and out of the camera cutout when the status bar is hidden (iOS does the same with its notch). `drawBehind` puts the screen under the top bar: with All but the top or None the rows show under it. Compare Details with List. iPhone Duo: the screen goes under the vertical bar at the side, so the blur under the bar reaches the edge, and the rows keep clear of it with `useSafeAreaInsets()`; `drawBehindVerticalBar: false` keeps the screen off it.',
  controls: {
    'layout.safeAreaEdges': presets({
      'All four': ['top', 'right', 'bottom', 'left'],
      'All but the top': ['right', 'bottom', 'left'],
      None: [],
      'Top only': ['top'],
      Sides: ['left', 'right'],
    }),
    'topBar.drawBehind': oneOf([true, false]),
    'layout.drawBehindVerticalBar': oneOf([true, false], { platform: 'ios' }),
  },
  keywords: ['safeAreaEdges', 'safeArea', 'drawBehind', 'insets'],
});
