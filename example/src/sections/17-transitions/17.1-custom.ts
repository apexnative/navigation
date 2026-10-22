import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.1',
  title: 'Transition presets',
  check:
    '`transition` of push and showModal: a ready animation of the screen and the same in reverse on the way back. `slideUp` comes from the bottom and goes down, `fade` and `scale` come and go in place, `none` shows at once (on Android once its screen is drawn, half a second at most, a push and a modal alike), `slideFromEnd` comes from the right (from the left in a right-to-left layout). A screen that draws nothing at first (`null`) does not hold its transition back: it comes in empty, with its bar, and its content shows as it renders. A preset takes a duration. `options.animations` stay for animations of your own and go over a preset. The top bar goes with its screen on Android (it comes, leaves, fades and grows with it; `animations.push.topBar` plays when the new screen hides the bar), and stays in place on iOS, where UIKit changes its title; `presentation` of createNavigation gives every push or modal of the app its transition.',
  runs: {
    'Push: slideUp': () => navigation.push('Details', { transition: 'slideUp' }),
    'Push: fade': () => navigation.push('Details', { transition: 'fade' }),
    'Push: scale': () => navigation.push('Details', { transition: 'scale' }),
    'Push: none': () => navigation.push('Details', { transition: 'none' }),
    'Push: fade, 800 ms': () =>
      navigation.push('Details', { transition: { type: 'fade', duration: 800 } }),
    'Push: an animation of its own': () =>
      navigation.push('Details', {
        options: {
          animations: {
            push: {
              content: {
                enter: {
                  translationX: { from: 240, to: 0, duration: 350 },
                  alpha: { from: 0, to: 1, duration: 350 },
                },
              },
            },
            pop: {
              content: {
                exit: {
                  translationX: { from: 0, to: 240, duration: 350 },
                  alpha: { from: 1, to: 0, duration: 350 },
                },
              },
            },
          },
        },
      }),
    'Push: an animation of its own, to a screen without the bar': () =>
      navigation.push('Details', {
        options: {
          topBar: { visible: false },
          animations: {
            push: {
              content: { enter: { translationY: { from: 400, to: 0, duration: 350 } } },
              topBar: { exit: { alpha: { from: 1, to: 0, duration: 350 } } },
            },
            pop: {
              content: { exit: { translationY: { from: 0, to: 400, duration: 350 } } },
            },
          },
        },
      }),
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
