import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.6',
  title: 'Waiting for the screen to draw',
  check:
    'The screen opened here pushes a screen whose body takes the time picked (300, 800 or 1500 ms) to render the first time. This is not data loading: the delay stands for a heavy first render, the JavaScript thread busy building the screen (a big list built at once); a screen that loads data renders at once with its own loader and needs no wait. Its title is static, in its options, so the title is in the bar on the first frame of every push; only the body is slow. `animations.push.waitForRender` as picked: `true` — the push starts once the body is drawn, and the screen slides in complete. — (unset) — the push waits half a second at most: with 300 ms the screen slides in complete; with 800 or 1500 ms the push goes at 0.5 s and the body comes in later. `false` — the push starts at once and the body always comes in later. "Push a plain screen" renders at once: it slides in complete for every value, no slower. "Push with a large title" waits as the slow screen does, and its large title is there on the first frame of the push, of Back and of the swipe back, for every value.',
  runs: {
    'Open the push demo': () => navigation.push('WaitForRenderDemo'),
  },
  keywords: ['waitForRender', 'push', 'useScreenOptions', 'largeTitle', 'animations'],
});
