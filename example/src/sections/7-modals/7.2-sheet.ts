import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.2',
  title: 'Sheet',
  check:
    'A sheet stops at its `detents`: drag it between them; a drag goes to the Journal as `sheetDetentChanged`. `selectedDetent` is where it opens; on Details a pick of it moves the sheet there, while a change of another option (`grabber`, `cornerRadius`) leaves the sheet where it was dragged. Up to `largestUndimmedDetent` the panel under it stays bright and takes touches, as under the sheet of Maps. `grabber` shows the handle; with `expandsOnScroll: false` only the top drags the sheet. Out of order, a repeat: the sheet stops at 300, medium and large, from the lowest up, and a drag down from 300 closes it. 200, 400 and large: three stops of numbers, which Android keeps all of. On Android it is the Material bottom sheet: Its content stops at half the height.',
  controls: {
    'modal.sheet.detents': presets({
      'Medium and large': ['medium', 'large'],
      'Medium only': ['medium'],
      'Its content': ['content'],
      '300 and large': [300, 'large'],
      '200, 400 and large': [200, 400, 'large'],
      'Out of order, a repeat': ['large', 300, 'medium', 'large'],
    }),
    'modal.sheet.selectedDetent': oneOf(['medium', 'large']),
    'modal.sheet.largestUndimmedDetent': oneOf(['medium']),
    'modal.sheet.grabber': oneOf([true, false]),
    'modal.sheet.expandsOnScroll': oneOf([false, true]),
    'modal.sheet.cornerRadius': oneOf([12, 32]),
  },
  keywords: [
    'sheet',
    'detents',
    'selectedDetent',
    'largestUndimmedDetent',
    'grabber',
    'sheetDetentChanged',
  ],
});
