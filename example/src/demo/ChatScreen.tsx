import React from 'react';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

const PEOPLE = ['Ann', 'Ben', 'Cleo'];

/**
 * A chat with one person: `key` in its definition keeps one chat per person in a stack. Opening a chat that is in the
 * stack already goes back to it.
 */
function Chat({ person }: { person: string }) {
  const navigation = useNavigation();
  return (
    <Screen>
      <Card title={`Chat with ${person}`}>
        <Muted>
          Open another chat, then this one again: the push goes back to this screen, the chats over
          it close.
        </Muted>
        <KeyValue label="Screen id" value={navigation.screen.id} />
        {PEOPLE.map((other) => (
          <Button
            key={other}
            title={other === person ? `Open ${other} again` : `Open ${other}`}
            onPress={() => navigation.push('Chat', { props: { person: other } })}
          />
        ))}
      </Card>
    </Screen>
  );
}

export const ChatScreen = defineScreen({
  component: Chat,
  key: (props) => props.person,
  options: (s, props) => s.title(props.person).hideBottomTabs(),
});
