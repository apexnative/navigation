import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '9.4',
  title: 'iPad: tab bar and sidebar',
  check:
    'On iPad (iOS 18+) the tabs are a bar at the top that turns into a sidebar: `sidebar` picks it, `sidebarLayout` puts the sidebar next to the content or over it. With a `customizationId` the user arranges the tabs.',
  platform: 'ipad',
  controls: {
    'bottomTabs.sidebar': oneOf(['automatic', 'tabBar', 'sidebar']),
    'bottomTabs.sidebarLayout': oneOf(['tile', 'overlap']),
    'bottomTabs.customizationId': oneOf(['demo-tabs']),
  },
  keywords: ['sidebar', 'sidebarLayout', 'customizationId', 'iPad'],
});
