import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.3',
  title: 'Shared elements',
  check:
    'A list of people. Tap a row: the avatar moves from the row and grows into the profile, and the name moves and grows into the full name of its title, a different text: the picture of the name turns into the picture of the full name in the first quarter of the way (`transition: { shared }`, two pairs from one `nativeID` to the other) while the rest fades; the text under them fades in after them (`elementTransitions` of its own). Back moves the avatar and the name home into the row. The card gallery: tap a card: the card grows into the top of the screen and its title into the heading (16 → 30), the text under them rises and fades in after them; Back moves them home, the text fades out first.',
  runs: {
    'Open the people list': () => navigation.push('People'),
    'Open the card gallery': () => navigation.push('Cards'),
  },
  keywords: [
    'sharedElementTransitions',
    'elementTransitions',
    'nativeID',
    'avatar',
    'card',
    'Text',
  ],
});
