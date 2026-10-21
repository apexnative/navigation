import { navigation, Screen, Stack, Tab, Tabs } from '@apexnative/navigation';
import { nativeProfile } from '../../demo/nativeProfiles';
import { icons } from '../../ui/icons';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.3',
  title: 'Native screens in a layout',
  check:
    'Native screens are parts of layouts too: tabs of a UIKit screen, a SwiftUI screen and a React screen. Each tab has its stack: Open a React screen pushes in it. Back to the Showcase is on the React tab.',
  platform: 'ios',
  runs: {
    'Tabs of native screens': () =>
      navigation.switchTo(
        Tabs(
          Tab(Stack(Screen('NativeProfile', nativeProfile)))
            .label('UIKit')
            .icon(icons.native),
          Tab(Stack(Screen('SwiftUIProfile', { ...nativeProfile, accentColor: '#00B894' })))
            .label('SwiftUI')
            .icon(icons.star),
          Tab(Stack(Screen('TabsDemo', { title: 'React' })))
            .label('React')
            .icon(icons.home)
        )
      ),
  },
  keywords: ['nativeScreen', 'Tabs', 'layout', 'UIKit', 'SwiftUI'],
});
