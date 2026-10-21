import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.1',
  title: 'Transition presets',
  check:
    '`transition` of push and showModal: a ready animation of the screen and the same in reverse on the way back. `slideUp` comes from the bottom and goes down, `fade` and `scale` come and go in place, `none` shows at once (on Android once its screen is drawn, half a second at most, a push and a modal alike), `slideFromEnd` comes from the right (from the left in a right-to-left layout). A screen that draws nothing at first (`null`) does not hold its transition back: it comes in empty, with its bar, and its content shows as it renders. A preset takes a duration. `options.animations` stay for animations of your own and go over a preset; `presentation` of createNavigation gives every push or modal of the app its transition.',
  runs: {
    'Push: slideUp': () => navigation.push('Details', { transition: 'slideUp' }),
    'Push: fade': () => navigation.push('Details', { transition: 'fade' }),
    'Push: scale': () => navigation.push('Details', { transition: 'scale' }),
    'Push: none': () => navigation.push('Details', { transition: 'none' }),
    'Push: fade, 800 ms': () =>
      navigation.push('Details', { transition: { type: 'fade', duration: 800 } }),
    'Push: fade, a screen that draws nothing for a second': () =>
      navigation.push('LateContent', { transition: 'fade' }),
    'Modal: none': () =>
      navigation.showModal('Details', {
        style: 'fullScreen',
        closeButton: true,
        transition: 'none',
      }),
    'Modal: slideFromEnd': () =>
      navigation.showModal('Details', {
        style: 'fullScreen',
        closeButton: true,
        transition: 'slideFromEnd',
      }),
  },
  keywords: [
    'transition',
    'animations',
    'fade',
    'slideUp',
    'scale',
    'slideFromEnd',
    'presentation',
  ],
});
