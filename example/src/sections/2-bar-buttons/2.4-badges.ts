import { presets } from '../controls';
import { defineScenario } from '../scenario';
import { md } from '../../ui/icons';

export default defineScenario({
  id: '2.4',
  title: 'Badges',
  check:
    'A badge on a button (iOS 26): a number, a short text, or `true` for a dot; `badgeColor` and `badgeTextColor` color it. On Details change it while it shows.',
  platform: 'ios',
  controls: {
    'topBar.rightButtons': presets({
      Count: [
        { id: 'inbox', text: 'Inbox', icon: { ios: 'tray', android: md('inbox') }, badge: 3 },
      ],
      Text: [
        { id: 'inbox', text: 'Inbox', icon: { ios: 'tray', android: md('inbox') }, badge: 'New' },
      ],
      Dot: [
        { id: 'inbox', text: 'Inbox', icon: { ios: 'tray', android: md('inbox') }, badge: true },
      ],
      'Badge colors': [
        {
          id: 'inbox',
          text: 'Inbox',
          icon: { ios: 'tray', android: md('inbox') },
          badge: 12,
          badgeColor: '#6C5CE7',
          badgeTextColor: '#FFFFFF',
        },
      ],
      'No badge': [
        { id: 'inbox', text: 'Inbox', icon: { ios: 'tray', android: md('inbox') }, badge: false },
      ],
    }),
  },
  keywords: ['badge', 'badgeColor', 'badgeTextColor'],
});
