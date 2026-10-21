import { navigation, Root, Screen, Stack } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '17.4',
  title: 'A new root',
  check:
    'The whole app replaced at once, as after a login: the new root comes in over the old one with the transition of the platform (a cross dissolve on iOS, the fade through on Android), with a preset (`fade`, `slideUp`, `scale`, `slideFromEnd`), at once with `none` (no empty frame), or with an `enter` of its own. With `enter.waitForRender: true` it waits for its content as long as it takes; on Android it then fades through as the others do. The new root goes back to the Showcase with a transition too, or at once.',
  runs: {
    'Switch: the platform': () => navigation.switchTo(Root(Stack(Screen('RootDemo')))),
    'Switch: none': () => navigation.switchTo(Root(Stack(Screen('RootDemo'))).transition('none')),
    'Switch: fade': () => navigation.switchTo(Root(Stack(Screen('RootDemo'))).transition('fade')),
    'Switch: slideUp': () =>
      navigation.switchTo(Root(Stack(Screen('RootDemo'))).transition('slideUp')),
    'Switch: scale': () => navigation.switchTo(Root(Stack(Screen('RootDemo'))).transition('scale')),
    'Switch: wait for content': () =>
      navigation.switchTo(
        Root(Stack(Screen('RootDemo'))).transition({ enter: { waitForRender: true } })
      ),
    'Switch: an animation of its own': () =>
      navigation.switchTo(
        Root(Stack(Screen('RootDemo'))).transition({
          enter: { translationX: { from: -500, to: 0, duration: 400 } },
        })
      ),
  },
  keywords: ['switchTo', 'Root', 'transition', 'setRoot', 'login'],
});
