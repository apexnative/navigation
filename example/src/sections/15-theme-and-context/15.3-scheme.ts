import { Appearance } from 'react-native';
import { navigation, Screen, Stack, Tab, Tabs } from '@apexnative/navigation';
import { icons } from '../../ui/icons';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '15.3',
  title: 'Light or dark set by the app',
  check:
    "With the system in light mode, Dark (`Appearance.setColorScheme('dark')`) turns the app dark, and so is everything shown after it. Show the overlay: its card is dark and says \"dark\", and the Showcase under it stays dark. Show tabs renders every tab at once (`tabsAttachMode: 'together'`): every tab is dark, and so is the Showcase when it comes back. The overlay's own Dark, Light and System buttons switch it and the app together while it shows; Close and show it again: the new one comes in the same scheme. Follow the system and the overlay's System pass `'unspecified'`. Try Light with the system in dark mode too. The Settings picker doesn't follow these buttons.",
  runs: {
    Dark: () => Appearance.setColorScheme('dark'),
    Light: () => Appearance.setColorScheme('light'),
    'Follow the system': () => Appearance.setColorScheme('unspecified'),
    'Show the overlay': () => navigation.showOverlay('SchemeOverlay'),
    'Show tabs': () =>
      navigation.switchTo(
        Tabs(
          Tab(Stack(Screen('TabsDemo', { title: 'Home' })))
            .label('Home')
            .icon({ ios: 'house', android: icons.home }),
          Tab(Stack(Screen('TabsDemo', { title: 'Inbox' })))
            .label('Inbox')
            .icon({ ios: 'tray', android: icons.bell }),
          Tab(Stack(Screen('TabsDemo', { title: 'Profile' })))
            .label('Profile')
            .icon(icons.person)
        ).options({ tabsAttachMode: 'together' })
      ),
  },
  keywords: [
    'Appearance',
    'setColorScheme',
    'useColorScheme',
    'dark mode',
    'overlay',
    'tabsAttachMode',
  ],
});
