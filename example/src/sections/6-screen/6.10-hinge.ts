import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.10',
  title: 'The hinge, live',
  check:
    'Open iPhone Duo (or an Android foldable) slowly: the number of degrees and the state follow `useHinge`, the lid of the drawing turns with `useHingeChange` without rendering the screen again. Closed, partly open, open; beyond 180° on Android devices that fold back. A device without a hinge says so.',
  runs: {
    'Show the hinge': () => navigation.push('HingeDemo'),
  },
  keywords: ['useHinge', 'useHingeChange', 'hinge', 'angle', 'iPhone Duo', 'foldable'],
});
