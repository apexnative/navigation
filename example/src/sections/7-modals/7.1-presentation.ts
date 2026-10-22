import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.1',
  title: 'How a modal shows',
  check:
    'Show opens Details as a modal. `modalPresentationStyle`: `pageSheet` a sheet over the screen, `formSheet` a smaller sheet on iPad, `fullScreen` the whole screen, `overFullScreen` the whole screen with the screen below kept under it. `modalTransitionStyle` is how it comes in (iOS): from below, a cross fade, a flip, a page curl — UIKit flips only a modal over the whole screen (`fullScreen`, `overFullScreen`); a sheet slides in and flips only as it goes. `partialCurl` curls only a `fullScreen` modal; with any other style the modal comes from below.',
  controls: {
    modalPresentationStyle: oneOf(['pageSheet', 'formSheet', 'fullScreen', 'overFullScreen']),
    modalTransitionStyle: oneOf(
      ['coverVertical', 'crossDissolve', 'flipHorizontal', 'partialCurl'],
      {
        platform: 'ios',
      }
    ),
  },
  keywords: [
    'showModal',
    'modalPresentationStyle',
    'modalTransitionStyle',
    'pageSheet',
    'fullScreen',
    'partialCurl',
  ],
});
