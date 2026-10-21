import React from 'react';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen } from '../ui';

/** The first screen of the group demo: the subtitle and the large title come from the group's options. */
function GroupFirst() {
  const nav = useNavigation();
  return (
    <Screen underBar>
      <Card title="A screen of a group">
        <Muted>Its subtitle and large title are the group's options; its title is its own.</Muted>
        <Button title="Push the second screen" onPress={() => nav.push('GroupSecond')} />
      </Card>
    </Screen>
  );
}

export const GroupFirstScreen = defineScreen({
  component: GroupFirst,
  options: (s) => s.title('First'),
});
