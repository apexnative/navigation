import { navigation } from '@apexnative/navigation';
import { nativeProfile } from '../../demo/nativeProfiles';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.2',
  title: 'A SwiftUI screen',
  check:
    'A SwiftUI view in a `UIHostingController`, registered the same way: the props, the counter to JS, Open a React screen and Close work as on the UIKit screen. Its title and large title come from `nativeScreen` options.',
  platform: 'ios',
  runs: {
    'Push the SwiftUI screen': () =>
      navigation.push('SwiftUIProfile', { props: { ...nativeProfile, accentColor: '#00B894' } }),
    'In a modal': () =>
      navigation.showModal('SwiftUIProfile', {
        props: { ...nativeProfile, accentColor: '#00B894' },
        closeButton: true,
      }),
  },
  keywords: ['nativeScreen', 'SwiftUI', 'UIHostingController', 'props'],
});
