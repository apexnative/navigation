import { navigation } from '@apexnative/navigation';
import { nativeProfile } from '../../demo/nativeProfiles';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.1',
  title: 'A screen of native views',
  check:
    'A screen written with native views — UIKit (Objective-C) on iOS, Android views on Android — registered natively with `registerNativeScreen` and in `screens` with `nativeScreen`: commands open it like any screen and its props reach it (listed on it). Its title comes from the options of `nativeScreen`. Send the counter calls a function of its props (`useProps`), which writes to the Journal; Open a React screen pushes Details over it and Close takes it away, commands the screen runs itself. On iOS it adds the ⓘ button itself and can push a plain UIKit view controller into the stack.',
  runs: {
    'Push the UIKit screen': () => navigation.push('NativeProfile', { props: nativeProfile }),
    'In a modal': () =>
      navigation.showModal('NativeProfile', { props: nativeProfile, closeButton: true }),
  },
  keywords: ['nativeScreen', 'registerNativeScreen', 'UIKit', 'Android views', 'useProps', 'props'],
});
