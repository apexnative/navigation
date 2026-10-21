import { navigation, Root, Screen, Stack } from '@apexnative/navigation';
import { roots } from '../../app/roots';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '19.2',
  title: 'A root with a modal',
  check:
    'A root with a modal over it, as a notification opens a screen in a modal at a cold start: the Showcase comes, then Details slides up over it with the animation of a modal. Dismiss on Details closes it, and the Showcase is under it.',
  runs: {
    'The Showcase with a modal': () =>
      navigation.switchTo(Root(roots.showcase).modal(Stack(Screen('Details')))),
  },
  keywords: ['Root', 'modal', 'deep link', 'notification', 'switchTo'],
});
