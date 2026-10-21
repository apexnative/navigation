import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.9',
  title: 'The fold, the camera and the hinge',
  check:
    '`useReservedRegions` gives the parts of the screen the hardware takes, drawn over it: `division`, the fold of a partly folded iPhone Duo or Android foldable (0 wide: keep controls about 20 points off it), and `occlusion`, a camera or a hinge over the screen. `useHinge` gives the state of the hinge and its angle. Fold the device or open it partly: both change. None on a device without them.',
  runs: {
    'Show the regions and the hinge': () => navigation.push('HardwareRegions'),
  },
  keywords: ['useReservedRegions', 'useHinge', 'useHingeChange', 'fold', 'hinge', 'iPhone Duo'],
});
