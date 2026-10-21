import React from 'react';
import { defineScreen } from '@apexnative/navigation';
import { Card, Muted, Screen } from '../ui';

/** The second screen of the group demo: the group's options too, and over them a large title of its own (off). */
function GroupSecond() {
  return (
    <Screen>
      <Card title="Another screen of the group">
        <Muted>The same subtitle from the group; its own options turn the large title off.</Muted>
      </Card>
    </Screen>
  );
}

export const GroupSecondScreen = defineScreen({
  component: GroupSecond,
  options: (s) => s.title('Second').largeTitle(false),
});
