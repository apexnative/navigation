import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.2',
  title: 'Zoom and preview',
  check:
    'A gallery. Tap a photo of the top row: it zooms open from its tile as a pushed screen (iOS 18+ `transition: { zoom }`); back and the swipe from the edge zoom it back. The bottom row opens as a modal with the same transition; a swipe down shrinks it into its tile. A long press on a photo previews it with a Share menu (`usePreview` on the `nativeID` of the tile): Share goes to the Journal, a tap on the preview opens it. The list has a hundred rows with a preview each: a long press on any row previews its photo, also after scrolling far down.',
  platform: 'ios',
  runs: {
    'Open the gallery': () => navigation.push('Gallery'),
    'Open a list': () => navigation.push('GalleryList'),
  },
  keywords: ['zoom', 'preview', 'usePreview', 'peek', 'context menu', 'nativeID'],
});
