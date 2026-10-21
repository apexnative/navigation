import React from 'react';
import { defineScreen, navigation, Root } from '@apexnative/navigation';
import { roots } from '../app/roots';
import { Button, Card, Muted, Screen } from '../ui';

/** A root of its own: back to the Showcase with a transition of the root. */
function RootDemo() {
  return (
    <Screen>
      <Card title="A new root">
        <Muted>The whole app was replaced with this screen. Go back with a transition too.</Muted>
        <Button
          title="Back with a fade"
          variant="primary"
          onPress={() => navigation.switchTo(Root(roots.showcase).transition('fade'))}
        />
        <Button
          title="Back from the bottom"
          onPress={() => navigation.switchTo(Root(roots.showcase).transition('slideUp'))}
        />
        <Button title="Back at once" onPress={() => navigation.switchTo('showcase')} />
      </Card>
    </Screen>
  );
}

export const RootDemoScreen = defineScreen({
  component: RootDemo,
  options: (s) => s.title('A new root'),
});
