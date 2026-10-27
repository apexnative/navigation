import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.3',
  title: 'Shared elements',
  check:
    'A list of people. Tap a row: the avatar moves from the row and grows into the profile, and the name moves and grows into the full name of its title, a different text: the picture of the name turns into the picture of the full name in the first quarter of the way (`transition: { shared }`, two pairs from one `nativeID` to the other) while the rest fades; the text under them fades in after them (`elementTransitions` of its own). Back moves the avatar and the name home into the row; (iOS) so does the swipe back, under the finger (`animations.pop.interactive`): stop halfway and they stay halfway, let go before half way and they fly back into the profile. The card gallery: tap a card: the card grows into the top of the screen and its title into the heading (16 → 30), the text under them rises and fades in after them; Back moves them home, the text fades out first. The turned shapes: each shape flies from its tile, turned and scaled as it shows there (its own `transform` and the scale of its tile), to its screen, turned as it is there, keeping its size on the way and turning the shorter way: 0° → 90° and 0° → 180° never flatten, 45° → −45° turns back through upright, 270° turns back a quarter, the shape in the half-size tile grows from the size it shows at, the shape with its own translation, turn and scale starts exactly where it shows, the mirrored one starts mirrored as it shows, the plain bar turns about its middle, the turned text straightens; the badge rises and fades in still turned −8°. Back flies each one home the same way. Same on iOS and Android.',
  runs: {
    'Open the people list': () => navigation.push('People'),
    'Open the card gallery': () => navigation.push('Cards'),
    'Open the turned shapes': () => navigation.push('Shapes'),
  },
  keywords: [
    'sharedElementTransitions',
    'elementTransitions',
    'nativeID',
    'avatar',
    'card',
    'Text',
    'transform',
    'rotate',
    'scale',
  ],
});
