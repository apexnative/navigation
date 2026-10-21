import { navigation, Screen, Stack, Tab, Tabs } from '@apexnative/navigation';
import { icons } from '../../ui/icons';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '13.3',
  title: 'Tab presses',
  check:
    'A demo of three tabs. A second press on the tab you are on scrolls it to the top (`useTabPress`, `wasSelected`). New does not get selected (`selectOnPress(false)`): its `onPress` opens a modal, as a compose button does. The presses go to the Journal. Back to the Showcase on a tab comes back.',
  runs: {
    'Open tabs with press handlers': () =>
      navigation.switchTo(
        Tabs(
          Tab(Stack(Screen('TabPressDemo', { title: 'Home' })))
            .label('Home')
            .icon(icons.home),
          Tab(Stack(Screen('TabPressDemo', { title: 'New' })))
            .label('New')
            .icon(icons.plus)
            .selectOnPress(false)
            .onPress(() => navigation.showModal('Details', { closeButton: true })),
          Tab(Stack(Screen('TabPressDemo', { title: 'Profile' })))
            .label('Profile')
            .icon(icons.person)
        )
      ),
  },
  keywords: ['useTabPress', 'onPress', 'selectOnPress', 'wasSelected', 'scroll to top'],
});
