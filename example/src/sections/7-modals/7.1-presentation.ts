import { navigation } from '@apexnative/navigation';
import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.1',
  title: 'How a modal shows',
  check:
    'Show opens Details as a modal. `modalPresentationStyle`: `pageSheet` a sheet over the dimmed screen; `formSheet` a smaller card on iPad, the same sheet elsewhere; `fullScreen` the whole screen; `overFullScreen` the whole screen with the screen below kept under it. On Android a sheet is the Material bottom sheet at the full height, at most 640 dp wide; `fullScreen` and `overFullScreen` come up from the bottom without a fade, 300 ms up and 250 ms down. Details has a background of its own, so nothing shows through it: A dialog over the screen shows a transparent screen with `overFullScreen`, a card over the dimmed screen that stays in sight around it. `modalTransitionStyle` is how it comes in (iOS): from below, a cross fade, a flip, a page curl — UIKit flips only a modal over the whole screen (`fullScreen`, `overFullScreen`); a sheet slides in and flips only as it goes. `partialCurl` curls only a `fullScreen` modal; with any other style the modal comes from below.',
  controls: {
    modalPresentationStyle: oneOf(['pageSheet', 'formSheet', 'fullScreen', 'overFullScreen']),
    modalTransitionStyle: oneOf(
      ['coverVertical', 'crossDissolve', 'flipHorizontal', 'partialCurl'],
      {
        platform: 'ios',
      }
    ),
  },
  runs: {
    'A dialog over the screen': () =>
      navigation.showModal('DialogOverlay', {
        style: 'overFullScreen',
        stack: false,
        options: {
          layout: { backgroundColor: 'transparent' },
          statusBar: { drawBehind: true },
        },
      }),
  },
  keywords: [
    'showModal',
    'modalPresentationStyle',
    'modalTransitionStyle',
    'pageSheet',
    'fullScreen',
    'overFullScreen',
    'partialCurl',
  ],
});
