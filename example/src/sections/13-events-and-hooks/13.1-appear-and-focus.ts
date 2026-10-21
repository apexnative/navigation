import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '13.1',
  title: 'Appear, disappear and focus',
  check:
    'A screen that shows what its hooks see. Push Details over it and come back: disappear, then appear; the `useFocusEffect` timer stops while Details covers it. A full screen modal does the same. Under a sheet the screen still shows and UIKit sends no disappear, but the user is in the sheet: the timer stops and `useIsFocused` is no; closing the sheet starts the timer again. `useNavigationState` names the screen the user sees; `useNavigationEvent` the last command. Appear, disappear and the runs and cleanups of `useFocusEffect` also go to the Journal.',
  runs: {
    'Push the hooks screen': () => navigation.push('HooksDemo'),
  },
  keywords: [
    'useScreenAppear',
    'useScreenDisappear',
    'useIsFocused',
    'useFocusEffect',
    'useNavigationState',
    'useNavigationEvent',
  ],
});
