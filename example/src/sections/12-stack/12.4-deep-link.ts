import { navigation, Screen } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.4',
  title: 'A stack at once: a deep link',
  check:
    'setStack gives the stack of the screen the user sees new screens all at once, as a deep link does: Sections, this panel open at 12.4, and four steps on top. Back goes through steps 4, 3, 2 and 1, then to this panel, then to Sections. Every screen of the stack is new — the panel you pressed on is gone — so the link carries what a screen needs to look as before (`focus: 12.4` here).',
  runs: {
    'Open as a deep link': () =>
      navigation.setStack(
        'Sections',
        Screen('Section', { id: 12, focus: '12.4' }),
        Screen('StackStep', { step: 1 }),
        Screen('StackStep', { step: 2 }),
        Screen('StackStep', { step: 3 }),
        Screen('StackStep', { step: 4 })
      ),
  },
  keywords: ['setStack', 'deep link', 'stack'],
});
