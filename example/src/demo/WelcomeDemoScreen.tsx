import React from 'react';
import { defineScreen, navigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen } from '../ui';

/** The `welcome` root of the Showcase: made from its param, the name of the user. */
function WelcomeDemo({ name }: { name: string }) {
  return (
    <Screen underBar>
      <Card title={`Hi, ${name}`}>
        <Muted>A root made from a param: navigation.switchTo('welcome', name).</Muted>
        <Button title="Sign out" variant="danger" onPress={() => navigation.switchTo('login')} />
        <Button title="Back to the Showcase" onPress={() => navigation.switchTo('showcase')} />
      </Card>
    </Screen>
  );
}

export const WelcomeDemoScreen = defineScreen({
  component: WelcomeDemo,
  options: (s, props: { name: string }) => s.title(`Hi, ${props.name}`).largeTitle(),
});
