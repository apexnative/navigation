import { navigation, Screen, Stack, Tab, Tabs } from '@apexnative/navigation';
import { icons } from '../../ui/icons';
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

export default defineScenario({
  id: '3.6',
  title: 'Hardware back',
  check:
    'The system back of Android (button or gesture). A draft with text keeps the user (`usePreventLeave`): back asks first, pushed or in a modal. `bottomTabsOnPress` (`Tabs().backBehavior`) is what back does on the first screen of a tab: the runs show demo tabs with it. `exit` leaves the app, `first` goes to the first tab (on the first tab it leaves), `previous` goes to the tab before (with none before it leaves): open Inbox, then Profile, and press back.',
  platform: 'android',
  runs: {
    'Push a draft': () => navigation.push('Draft'),
    'A draft in a modal': () => navigation.showModal('Draft'),
    'Tabs: back exits': () => tabsWithBack('exit'),
    'Tabs: back to the first tab': () => tabsWithBack('first'),
    'Tabs: back to the tab before': () => tabsWithBack('previous'),
  },
  keywords: ['hardwareBackButton', 'usePreventLeave', 'bottomTabsOnPress', 'backBehavior'],
});
