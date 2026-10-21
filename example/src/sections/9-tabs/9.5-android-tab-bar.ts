import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '9.5',
  title: 'The tab bar of Android',
  check:
    'The Material tab bar: `layoutStyle` stretched over the width (it reaches under the navigation bar) or `compact` — as wide as its tabs, in the middle, floating above the navigation bar; with `cornerRadius: 28`, `bottomMargin: 16` and `drawBehind` it is a pill over the rows. `elevation` its shadow, `hideOnScroll` hides it as the rows scroll down (the rows go on under it); Push in the bar shows a screen over the rows, and tabs hidden by the scroll show again for it, as the top bar does.',
  platform: 'android',
  controls: {
    'bottomTabs.layoutStyle': oneOf(['stretch', 'compact']),
    'bottomTabs.cornerRadius': oneOf([0, 16, 28]),
    'bottomTabs.bottomMargin': oneOf([0, 16]),
    'bottomTabs.elevation': oneOf([0, 8]),
    'bottomTabs.hideOnScroll': oneOf([true, false]),
    'bottomTabs.drawBehind': oneOf([true, false]),
  },
  keywords: [
    'layoutStyle',
    'cornerRadius',
    'bottomMargin',
    'elevation',
    'hideOnScroll',
    'drawBehind',
  ],
});
