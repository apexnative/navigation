import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.6',
  title: 'Waiting for the screen to draw',
  check:
    'The screen opened here pushes a screen whose content takes the time picked (300, 800 or 1500 ms) to render the first time; its title comes with the push. It simulates a heavy first render (the JavaScript thread busy building the screen, like a big list built at once), not data loading: a screen that loads data renders at once with its own loader and needs no wait. `animations.push.waitForRender` as picked: `true` — the push starts only once the content is drawn, and the screen slides in complete. — (unset) — the push waits for the content half a second at most: with 300 ms it slides in complete, with 800 or 1500 ms it goes at 0.5 s and slides in empty, the content coming in later. `false` — the push starts at once, the screen slides in empty and its content comes in later. "Push a plain screen" draws at once: it slides in complete for every value, no slower. "Push with a large title": the large title moves in with its screen on push, Back and the swipe back for every value; on iOS unset doesn\'t wait for such a screen, its content comes in during the push.',
  runs: {
    'Open the push demo': () => navigation.push('WaitForRenderDemo'),
  },
  keywords: ['waitForRender', 'push', 'useScreenOptions', 'largeTitle', 'animations'],
});
