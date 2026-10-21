import React, { useState } from 'react';
import { Alert } from 'react-native';
import { defineScreen, useNavigation, usePreventLeave } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Button, Card, Muted, Screen, TextField } from '../ui';

/**
 * A draft that keeps the user while it has text: `usePreventLeave` holds back, the swipe back, the system back, the
 * swipe down of a modal and its close button, and asks before the draft goes.
 */
function Draft() {
  const navigation = useNavigation();
  const [text, setText] = useState('An unsaved draft');
  usePreventLeave(text.length > 0, () => {
    log('event', 'usePreventLeave', 'the user tried to leave');
    Alert.alert('Discard the draft?', undefined, [
      { text: 'Keep editing', style: 'cancel' },
      { text: 'Discard', style: 'destructive', onPress: () => navigation.goBack() },
    ]);
  });
  return (
    <Screen>
      <Card title="A draft">
        <Muted>
          While the field has text, every way out asks first; Discard leaves with goBack(). Clear
          the field, and the screen leaves as any other.
        </Muted>
        <TextField placeholder="Write something" value={text} onChangeText={setText} />
        <Button title="Clear" onPress={() => setText('')} />
      </Card>
    </Screen>
  );
}

export const DraftScreen = defineScreen({
  component: Draft,
  options: (s) => s.title('Draft').hideBottomTabs(),
});
