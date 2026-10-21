import React, { useState } from 'react';
import { defineScreen, useScreen } from '@apexnative/navigation';
import { Button, Card, Muted, Screen } from '../ui';

// Until when the data of each screen does not come, by screen id.
const downUntil = new Map<string, number>();

/**
 * A screen that fails: at the start while its data does not come (`downFor` ms), or later, when Break it makes its
 * next render throw. The app shows `errorFallback` in its place.
 */
function Broken({ downFor }: { downFor?: number }) {
  const { id } = useScreen();
  const [broken, setBroken] = useState(false);
  if (downFor && !downUntil.has(id)) downUntil.set(id, Date.now() + downFor);
  if (Date.now() < (downUntil.get(id) ?? 0)) {
    throw new Error('The data of this screen did not come. Try again in a few seconds.');
  }
  if (broken) throw new Error('This screen broke while it showed.');
  return (
    <Screen>
      <Card title="A screen that works">
        <Muted>Break it: its next render throws, and the screen shows errorFallback.</Muted>
        <Button title="Break it" variant="danger" onPress={() => setBroken(true)} />
      </Card>
    </Screen>
  );
}

export const BrokenScreen = defineScreen({
  component: Broken,
  options: (s) => s.title('Errors').hideBottomTabs(),
});
