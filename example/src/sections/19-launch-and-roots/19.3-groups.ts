import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '19.3',
  title: 'Groups of screens',
  check:
    '`groups` of createNavigation: screens that share options, here a subtitle and a large title. The first screen shows both with a title of its own; the second has the subtitle too and turns the large title off with its own options. Commands name the screens of a group as any other.',
  runs: {
    'Push a screen of the group': () => navigation.push('GroupFirst'),
  },
  keywords: ['groups', 'options', 'subtitle', 'createNavigation'],
});
