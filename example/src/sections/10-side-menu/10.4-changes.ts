import { defineScenario } from '../scenario';

export default defineScenario({
  id: '10.4',
  title: 'Changes while it shows',
  check:
    'Show, then open a menu. Its size buttons merge `width` (and on Android `height`) into the menu screen: the open menu takes the new size at once. A width merged into the menu screen counts while that menu is open; after it closes the options of the center count again (a height stays). Open the left menu at once (on Home: `visible: true, animate: false`): the Journal gets `screenWillAppear` of the menu screen, then its `screenDidAppear`, as with an animated open and a swipe.',
  keywords: ['width', 'height', 'mergeOptions', 'animate', 'screenWillAppear', 'sideMenu'],
});
