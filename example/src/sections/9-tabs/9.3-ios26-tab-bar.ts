import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '9.3',
  title: 'The tab bar of iOS 26',
  check:
    '`minimizeBehavior`: the tab bar shrinks to the selected tab as the rows scroll down (`onScrollDown`) or up, and comes back. `bottomAccessory` is a row above the tabs, as the mini player of a music app (here `TabsAccessory`); it moves next to the shrunk tab bar.',
  platform: 'ios',
  controls: {
    'bottomTabs.minimizeBehavior': oneOf(['automatic', 'never', 'onScrollDown', 'onScrollUp']),
    'bottomTabs.bottomAccessory': presets({
      'Now playing': { component: { name: 'TabsAccessory' } },
    }),
  },
  keywords: ['minimizeBehavior', 'bottomAccessory', 'accessory'],
});
