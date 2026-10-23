import { navigation, Screen, Stack, Tab, Tabs } from '@apexnative/navigation';
import { icons } from '../../ui/icons';
import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

// A demo app of three tabs, back to the Showcase on each tab: back on the first screen of a tab does what it says.
const tabsWithBack = (bottomTabsOnPress: 'exit' | 'first' | 'previous') =>
  navigation.switchTo(
    Tabs(
      Tab(Stack(Screen('TabsDemo', { title: 'Home' })))
        .label('Home')
        .icon(icons.home),
      Tab(Stack(Screen('TabsDemo', { title: 'Inbox' })))
        .label('Inbox')
        .icon(icons.bell),
      Tab(Stack(Screen('TabsDemo', { title: 'Profile' })))
        .label('Profile')
        .icon(icons.person)
    ).backBehavior(bottomTabsOnPress)
  );

// A demo app of one screen without the system's animation of going home: back on it leaves at once.
const rootWithoutSystemAnimation = () =>
  navigation.switchTo(
    Stack(
      Screen('TabsDemo', { title: 'Home' }).options({
        hardwareBackButton: { systemAnimation: false },
      })
    )
  );

export default defineScenario({
  id: '3.6',
  title: 'Hardware back',
  check:
    'The system back of Android (button or gesture). On Android 14+ a slow swipe from the edge on a pushed screen previews the pop: the screen shrinks toward the swipe with rounded corners, the screen under it shows dimmed with its bar; letting go pops, swiping back cancels. A draft with text keeps the user (`usePreventLeave`): back asks first, pushed or in a modal. `bottomTabsOnPress` (`Tabs().backBehavior`) is what back does on the first screen of a tab: the runs show demo tabs with it. `exit` leaves the app, `first` goes to the first tab (on the first tab it leaves), `previous` goes to the tab before (with none before it leaves): open Inbox, then Profile, and press back. `animations.pop.preview: false` on Details: the slow swipe shows no preview, Details pops when the finger lifts. "A root without the system animation" (`hardwareBackButton.systemAnimation: false`): a slow swipe back on its only screen shows no shrinking app and no home screen behind; letting go leaves the app at once.',
  platform: 'android',
  controls: {
    'animations.pop.preview': oneOf([false, true]),
  },
  runs: {
    'Push a draft': () => navigation.push('Draft'),
    'A draft in a modal': () => navigation.showModal('Draft'),
    'Tabs: back exits': () => tabsWithBack('exit'),
    'Tabs: back to the first tab': () => tabsWithBack('first'),
    'Tabs: back to the tab before': () => tabsWithBack('previous'),
    'A root without the system animation': () => rootWithoutSystemAnimation(),
  },
  keywords: [
    'hardwareBackButton',
    'usePreventLeave',
    'bottomTabsOnPress',
    'backBehavior',
    'predictive back',
    'preview',
    'systemAnimation',
  ],
});
