import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.1',
  title: 'Columns',
  check:
    'Show switches to a split view: Library (the sidebar) beside Details; a folder picked in Library shows in Details with `showInColumn`, and a Library over Details goes away. `displayMode` is how it starts: `oneBesideSecondary` side by side, `oneOverSecondary` Library over Details, `secondaryOnly` Details alone (the sidebar button brings Library). `splitBehavior`: `tile` side by side, `overlay` over, `displace` pushes Details aside. `primaryEdge: trailing` puts Library on the right. `width` is the width Library asks for, within `maxWidth`. A change of the columns goes to the Journal as `splitViewDisplayModeChanged`; the buttons on Details change them with setOptions.',
  platform: 'ipad',
  controls: {
    'splitView.displayMode': oneOf(['oneBesideSecondary', 'oneOverSecondary', 'secondaryOnly']),
    'splitView.splitBehavior': oneOf(['tile', 'overlay', 'displace']),
    'splitView.primaryEdge': oneOf(['leading', 'trailing']),
    'splitView.width': oneOf([240, 320, 420]),
    'splitView.maxWidth': oneOf([320, 480]),
  },
  keywords: [
    'splitView',
    'displayMode',
    'splitBehavior',
    'primaryEdge',
    'width',
    'splitViewDisplayModeChanged',
  ],
});
