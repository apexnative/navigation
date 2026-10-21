import { color } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.6',
  title: 'Background',
  check:
    "`backgroundColor` colors the screen, behind its React content too; the Showcase's theme sets it. `componentBackgroundColor` covers it behind the React content, to tell the two apart.",
  controls: {
    'layout.componentBackgroundColor': color(),
    'layout.backgroundColor': color(),
  },
  keywords: ['backgroundColor', 'componentBackgroundColor'],
});
