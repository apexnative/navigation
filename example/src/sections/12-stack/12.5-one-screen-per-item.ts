import { navigation } from '@apexnative/navigation';
import { defineScenario } from '../scenario';

export default defineScenario({
  id: '12.5',
  title: 'One screen per item',
  check:
    'The chat screen has `key: (props) => props.person`. The run pushes the chats with Ann, Ben and Ann again: the last push goes back to the first chat with Ann (the same screen id), and Ben’s chat is gone — back from Ann comes here. In a chat, “Open Ann again” on Ann’s chat changes nothing.',
  runs: {
    'Ann, Ben, then Ann again': async () => {
      await navigation.push('Chat', { props: { person: 'Ann' } });
      await navigation.push('Chat', { props: { person: 'Ben' } });
      await navigation.push('Chat', { props: { person: 'Ann' } });
    },
  },
  keywords: ['key', 'defineScreen', 'push', 'one screen per item', 'chat'],
});
