import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.3',
  title: 'Title component',
  check:
    'A React component in place of the title: `title.component` names a component of `screens` (here `TitleView`, a star in a pill), with `passProps` as its props. `alignment` goes with any of them: `center` gives it the size of its content in the middle of the bar, `fill` the whole room between the side buttons, from the back button on: the pill of TitleView stays in the middle of that room, the post header in Examples starts next to the back button. `Chat title` (an avatar, a name and a status) with `fill`, and `glass` of 1.1, is a title as chats show it.',
  controls: {
    'topBar.title.component': presets({
      TitleView: { name: 'TitleView' },
      'With props': { name: 'TitleView', passProps: { text: 'From props' } },
      'Chat title': { name: 'ChatTitle' },
    }),
    'topBar.title.component.alignment': oneOf(['center', 'fill']),
  },
  keywords: ['title.component', 'passProps', 'alignment'],
});
