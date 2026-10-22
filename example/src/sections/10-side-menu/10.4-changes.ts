import { defineScenario } from '../scenario';

export default defineScenario({
  id: '10.4',
  title: 'Changes while it shows',
  check:
    'Show, then open a menu. Its size buttons merge `width` (and on Android `height`) into the menu screen: the open menu takes the new size at once. A width merged into the menu screen counts while that menu is open; after it closes the options of the center count again (a height stays).',
  keywords: ['width', 'height', 'mergeOptions', 'sideMenu'],
});
