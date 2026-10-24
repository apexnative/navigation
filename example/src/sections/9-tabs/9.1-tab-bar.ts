import { color, oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '9.1',
  title: 'The tab bar',
  check:
    'Show switches to a demo app of three tabs with these options; Back to the Showcase on any tab comes back. `titleDisplayMode` shows or hides the titles under the icons; `currentTabIndex` is the tab it opens on. On iOS 26 the tab bar is Liquid Glass: `backgroundColor` and `borderColor` apply on Android (and on iOS before 26 or in the compatibility mode, `UIDesignRequiresCompatibility`: iOS 26, and iOS 27 only for an app built with an older SDK, with `translucent`, `hideShadow` and `barStyle`).',
  controls: {
    'bottomTabs.titleDisplayMode': oneOf(['alwaysShow', 'showWhenActive', 'alwaysHide']),
    'bottomTabs.currentTabIndex': oneOf([0, 1, 2]),
    'bottomTabs.backgroundColor': color({ platform: 'android' }),
    'bottomTabs.borderColor': color({ platform: 'android' }),
  },
  keywords: [
    'bottomTabs',
    'titleDisplayMode',
    'currentTabIndex',
    'backgroundColor',
    'Liquid Glass',
  ],
});
