import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

const share = {
  id: 'share',
  text: 'Share',
  icon: { ios: 'square.and.arrow.up', android: md('share') },
};
const copy = { id: 'copy', text: 'Copy', icon: { ios: 'doc.on.doc', android: md('content_copy') } };
const flag = { id: 'flag', text: 'Flag', icon: { ios: 'flag', android: md('flag') } };
const favorite = {
  id: 'favorite',
  text: 'Favorite',
  icon: { ios: 'heart', android: md('favorite') },
};
const bookmark = {
  id: 'bookmark',
  text: 'Bookmark',
  icon: { ios: 'bookmark', android: md('bookmark') },
};

export default defineScenario({
  id: '2.12',
  title: 'Few places: priority and vertical bars',
  check:
    'Five right buttons in a bar with room for fewer: `priority: high` keeps Bookmark in the bar the longest, `low` sends Share to the overflow menu first (iOS 27, Android; on Android the bar keeps the list order). On iPhone Duo the buttons go into a vertical bar at the side: a `component` button stays in the row of the title unless `vertical: true`; `verticalBarKeeps: buttons` gives the buttons room over the tabs; `layout.verticalBars: false` keeps the bars of the screen horizontal.',
  controls: {
    'topBar.rightButtons': presets({
      'Bookmark high, Share low': [
        { ...share, priority: 'low' },
        copy,
        flag,
        favorite,
        { ...bookmark, priority: 'high' },
      ],
      'A component that may go vertical': [
        { id: 'like', component: { name: 'BarButtonView' }, vertical: true },
        share,
      ],
    }),
    'topBar.verticalBarKeeps': oneOf(['tabs', 'buttons'], { platform: 'ios' }),
    'layout.verticalBars': oneOf([true, false], { platform: 'ios' }),
  },
  keywords: ['priority', 'vertical', 'verticalBarKeeps', 'verticalBars', 'iPhone Duo'],
});
