import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.2',
  title: 'The commands of a stack',
  check:
    'A modal with a stack of its own, so nothing here touches the Showcase. On a step: push the next one; `pop` goes back one; from step 3 on, `popTo` goes back to step 1 by its id; `popToRoot` goes to the first screen of the stack; `goBack` pops on a step over another and closes the modal on its first screen (canPop · canDismiss show why); `replace` leaves step 1 alone in the stack, with no back; `setStack` puts steps 1, 2 and 3 in place of the stack — step 3 shows, back goes to 2. ✕ closes the modal.',
  runs: {
    'Open steps in a modal': () =>
      navigation.showModal('StackStep', { props: { step: 1, modal: true }, closeButton: true }),
  },
  keywords: ['popTo', 'popToRoot', 'goBack', 'canPop', 'canDismiss', 'replace', 'setStack'],
});
