import { presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '9.6',
  title: 'The custom row',
  check:
    'With `customRow`, every tab draws its item with a component (`bottomTab.component`) and the row holds them over the bar: tap a cell to switch tabs, the selected one has a pill, and Badge counts up the badge of its tab’s cell. Tall makes the row 80 high, growing upward, and the rows of the screen end above it; Lifted floats it 16 above the bar’s bottom with smaller corners (a touch under it reaches the screen); Clear has no background. The row goes with the bar: Push hides both and Back brings them back, and with `hideOnScroll` of 9.5 it hides as the rows scroll down. A sheet or a modal from the tab covers the row; the keyboard of the text field doesn’t move it off its place.',
  controls: {
    'bottomTabs.customRow': presets({
      Row: {},
      Tall: { height: 80 },
      Lifted: { bottomMargin: 16, cornerRadius: 16 },
      Clear: { backgroundEffect: 'none' },
    }),
  },
  keywords: ['customRow', 'component', 'bottomTab.component', 'backgroundEffect'],
});
