import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.3',
  title: 'Inspector',
  check:
    'On iPadOS 26 a column at the trailing edge; with an option of this group set the demo has one (Info, facts about the picked item). `inspector.visible` shows it or hides it once the split view appears; Show the inspector and Hide the inspector on Details change it with setOptions, and the Journal gets `splitViewDisplayModeChanged` with `inspectorVisible`. `inspector.width` is its width. Earlier iPadOS leaves the inspector out with a warning.',
  platform: 'ipad',
  controls: {
    'splitView.inspector.visible': oneOf([true, false]),
    'splitView.inspector.width': oneOf([280, 360]),
  },
  keywords: ['inspector', 'iPadOS 26', 'inspectorVisible'],
});
