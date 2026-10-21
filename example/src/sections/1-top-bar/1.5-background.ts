import { color, oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '1.5',
  title: 'Background',
  check:
    'The background of the bar. iOS 26 keeps its glass unless a color is set. With a color: a large title stays seen (the color goes into the list behind it), and the list has no edge effect at the top. `translucent`, `blur` and `barStyle` are iOS: `translucent: false` makes a solid bar, white in the light theme and black in the dark one; `true` is the glass, as without it on iOS 26; `blur` is a frosted blur. A color wins over `blur`, and `blur` over `translucent`; scroll the list under the bar to see them. `component` names a component of `screens` drawn behind the bar (here `BarBackground`, stripes); `clipToBounds` (iOS) keeps it inside the bar.',
  controls: {
    'topBar.background.color': color(),
    'topBar.background.translucent': oneOf([true, false], { platform: 'ios' }),
    'topBar.background.blur': oneOf([true, false], { platform: 'ios' }),
    'topBar.barStyle': oneOf(['default', 'black'], { platform: 'ios' }),
    'topBar.background.component': presets({ Stripes: { name: 'BarBackground' } }),
    'topBar.background.clipToBounds': oneOf([true, false], { platform: 'ios' }),
  },
  keywords: ['background', 'translucent', 'blur', 'barStyle', 'clipToBounds'],
});
