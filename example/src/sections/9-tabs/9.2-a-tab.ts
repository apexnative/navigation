import { md } from '../../ui/icons';
import { color, oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '9.2',
  title: 'The tabs',
  check:
    'The tabs of the demo. The colors and the font go on every tab: the selected tab takes `selectedIconColor` and `selectedTextColor`; on iOS 26 the system draws the others in its own color — the one exception is an image icon, which keeps `iconColor` (the Profile tab has one; Home and Inbox have symbols); `textColor` of a tab not selected is Android. The `badge`, its `badgeColor`, the dot, the `icon` (an SF Symbol on iOS, a Material Symbol on Android) and `iconWidth` of the icon (Android; 24.5 is 24.5 dp, not 24) go on the Inbox tab. `switchAnimation` (Android) goes on the tab bar: `platform` fades through on a switch, `none` switches at once; the buttons on a tab merge it into the screen of that tab, which decides the switches to it while it is on top of the tab: after Push Details, the pushed screen decides.',
  controls: {
    'bottomTab.badge': presets({ '3': '3', New: 'New' }),
    'bottomTab.badgeColor': color(),
    'bottomTab.dotIndicator': presets({ Dot: { visible: true } }),
    'bottomTab.icon': presets({
      'Full tray': { ios: 'tray.full', android: md('inbox') },
      Envelope: { ios: 'envelope', android: md('mail') },
    }),
    'bottomTab.selectedIconColor': color(),
    'bottomTab.selectedTextColor': color(),
    'bottomTab.iconColor': color(),
    'bottomTab.textColor': color({ platform: 'android' }),
    'bottomTab.fontWeight': oneOf(['regular', 'bold']),
    'bottomTab.iconWidth': oneOf([20, 24.5, 32], { platform: 'android' }),
    'bottomTabs.switchAnimation': oneOf(['platform', 'none'], { platform: 'android' }),
  },
  keywords: [
    'bottomTab',
    'badge',
    'dotIndicator',
    'icon',
    'SF Symbol',
    'selectedIconColor',
    'iconWidth',
    'switchAnimation',
  ],
});
