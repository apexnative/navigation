import { presets } from '../controls';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '6.5',
  title: 'Keyboard shortcuts',
  check:
    'Shortcuts of the screen with a hardware keyboard (iOS): a press goes to the Journal as `keyCommandPressed`. On iPad hold ⌘ to see them listed. In the Simulator the keys of the Mac reach the app with the hardware keyboard on (I/O → Keyboard).',
  platform: 'ios',
  controls: {
    keyCommands: presets({
      'New and Find': [
        { id: 'new', input: 'n', modifiers: ['command'], title: 'New' },
        { id: 'find', input: 'f', modifiers: ['command'], title: 'Find' },
      ],
      'With Shift': [
        { id: 'export', input: 'e', modifiers: ['command', 'shift'], title: 'Export' },
      ],
      None: [],
    }),
  },
  keywords: ['keyCommands', 'keyboard', 'shortcut', 'keyCommandPressed'],
});
