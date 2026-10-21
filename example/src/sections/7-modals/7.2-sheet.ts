import { oneOf, presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '7.2',
  title: 'Sheet',
  check:
    'A sheet stops at its `detents`: drag it between them; a drag goes to the Journal as `sheetDetentChanged`. `selectedDetent` is where it opens. Up to `largestUndimmedDetent` the panel under it stays bright and takes touches, as under the sheet of Maps. `grabber` shows the handle; with `expandsOnScroll: false` only the top drags the sheet. On Android it is the Material bottom sheet: Its content stops at half the height.',
  controls: {
    'modal.sheet.detents': presets({
      'Medium and large': ['medium', 'large'],
      'Medium only': ['medium'],
      'Its content': ['content'],
      '300 and large': [300, 'large'],
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
