import { oneOf } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '11.2',
  title: 'Three columns',
  check:
    'With an option of this group set the demo has a middle column: the items of the folder picked in Library; an item picked there shows in Details. `displayMode`: `twoBesideSecondary` all three side by side, `twoOverSecondary` Library and the items over Details, `twoDisplaceSecondary` they push Details aside. `supplementary.width` is the width of the middle column. `showsSecondaryOnlyButton` adds a button that leaves Details alone.',
  platform: 'ipad',
  controls: {
    'splitView.displayMode': oneOf([
      'twoBesideSecondary',
      'twoOverSecondary',
      'twoDisplaceSecondary',
    ]),
    'splitView.supplementary.width': oneOf([280, 360]),
    'splitView.showsSecondaryOnlyButton': oneOf([true, false]),
  },
  keywords: ['supplementary', 'three columns', 'showsSecondaryOnlyButton', 'displayMode'],
});
