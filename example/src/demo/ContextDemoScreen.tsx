import React from 'react';
import {
  defineScreen,
  navigation,
  useAppContext,
  useNavigation,
  useScreenOptions,
} from '@apexnative/navigation';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

/**
 * The app's context in a screen: `useAppContext` reads it, `navigation.setContext` changes it, and every screen that
 * reads it renders again — in a modal over it too. The title follows it. The screen selects only the user: a change of
 * another part of the context doesn't render it.
 */
function ContextDemo() {
  const nav = useNavigation();
  const user = useAppContext((context) => context.user);
  useScreenOptions((s) => s.title(user ? `Hi, ${user}` : 'Not signed in'));
  return (
    <Screen>
      <Card title="The app's context">
        <KeyValue label="user" value={user ?? 'null'} />
        <Button
          title="Sign in as Sam"
          variant="primary"
          onPress={() => navigation.setContext((context) => ({ ...context, user: 'Sam' }))}
        />
        <Button
          title="Sign out"
          onPress={() => navigation.setContext((context) => ({ ...context, user: null }))}
        />
      </Card>
      <Card title="Everywhere at once">
        <Muted>
          The same screen in a modal reads the same context: change it there and close it.
        </Muted>
        <Button
          title="Open it in a modal"
          onPress={() => nav.showModal('ContextDemo', { closeButton: true })}
        />
      </Card>
    </Screen>
  );
}

export const ContextDemoScreen = defineScreen({
  component: ContextDemo,
  options: (s) => s.hideBottomTabs(),
});
