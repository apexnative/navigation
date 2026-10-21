import React from 'react';
import { defineScreen, navigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen } from '../ui';

/** The `login` root of the Showcase: a signed-out app. Signing in switches to the `welcome` root with a name. */
function LoginDemo() {
  return (
    <Screen>
      <Card title="Signed out">
        <Muted>A root of its own, by name: navigation.switchTo('login').</Muted>
        <Button
          title="Sign in as Sam"
          variant="primary"
          onPress={() => navigation.switchTo('welcome', 'Sam')}
        />
        <Button title="Back to the Showcase" onPress={() => navigation.switchTo('showcase')} />
      </Card>
    </Screen>
  );
}

export const LoginDemoScreen = defineScreen({
  component: LoginDemo,
  options: (s) => s.title('Login'),
});
