import { navigation } from '@apexnative/navigation';
import { counter } from '../../demo/counter';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '18.4',
  title: 'Data from the app',
  check:
    "A native screen — SwiftUI on iOS, Android views on Android — with `useProps`: the hook runs in the app while the screen is open, and what it returns are its props: the count of a store outside React and the user of the app's context. +1 calls a function of the props: the store changes in JS and the new count comes back to the screen, which writes what it sees to the Journal. Sign in as Ann and Sign out change the context. Push and count from JS adds one every second for five seconds while the screen is open.",
  runs: {
    'Push the screen': () => navigation.push('UsePropsDemo'),
    'Push and count from JS': () => {
      navigation.push('UsePropsDemo');
      let ticks = 0;
      const timer = setInterval(() => {
        counter.increment();
        ticks += 1;
        if (ticks === 5) clearInterval(timer);
      }, 1000);
    },
  },
  keywords: ['useProps', 'nativeScreen', 'useAppContext', 'store', 'SwiftUI', 'Android views'],
});
