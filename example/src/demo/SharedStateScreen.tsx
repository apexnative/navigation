import React from 'react';
import { defineScreen, useNavigation, useScreenOptions } from '@apexnative/navigation';
import { oneTree, setOneTree } from '../app/launchMode';
import { useSavedItems } from '../app/SavedItems';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

/**
 * The store of the Showcase in a screen: one store for every screen in one tree, a store per screen otherwise. The
 * title counts too.
 */
function SharedState() {
  const nav = useNavigation();
  const saved = useSavedItems();
  const count = saved?.count ?? 0;
  useScreenOptions((s) => s.title(`Saved: ${count}`));
  return (
    <Screen>
      <Card title={oneTree ? 'One React tree (appProvider)' : 'A root per screen (screenProvider)'}>
        <Muted>
          {oneTree
            ? 'Every screen reads one store: save here, in a modal or on a pushed screen, and all of them count it.'
            : 'Each screen has a store of its own: a modal or a pushed screen starts from 0.'}
        </Muted>
        <KeyValue label="Saved items" value={count} />
        <Button title="Save an item" variant="primary" onPress={() => saved?.save()} />
        <Button title="Push another" onPress={() => nav.push('SharedState')} />
        <Button
          title="Open in a modal"
          onPress={() => nav.showModal('SharedState', { closeButton: true })}
        />
      </Card>
      <Card title="The mode">
        <Muted>Switching reloads the app; it starts in the other mode.</Muted>
        <Button
          title={oneTree ? 'A root per screen' : 'One React tree'}
          onPress={() => setOneTree(!oneTree)}
        />
      </Card>
    </Screen>
  );
}

export const SharedStateScreen = defineScreen({
  component: SharedState,
  options: (s) => s.hideBottomTabs(),
});
