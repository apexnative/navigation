import React, { useState } from 'react';
import { defineScreen, useNavigationButton, useScreenOptions } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Card, KeyValue, Muted, Screen } from '../ui';

/**
 * Bar buttons from the state of the screen: Like has `onPress` and its text follows the count; Refresh has no handler
 * of its own, `useNavigationButton` takes its presses by id.
 */
function ButtonHooks() {
  const [likes, setLikes] = useState(0);
  const [refreshed, setRefreshed] = useState(0);

  useNavigationButton('refresh', () => {
    setRefreshed((count) => count + 1);
    log('event', 'useNavigationButton', 'refresh');
  });
  useScreenOptions((s) =>
    s
      .title(`Likes: ${likes}`)
      .rightButtons(
        { id: 'refresh', text: 'Refresh' },
        { id: 'like', text: `Like ${likes}`, onPress: () => setLikes((count) => count + 1) }
      )
  );

  return (
    <Screen>
      <Card title="Buttons from state">
        <Muted>
          Like: `onPress` in useScreenOptions, the title and its text follow the count. Refresh: no
          handler in the options, useNavigationButton takes it by id.
        </Muted>
        <KeyValue label="Likes" value={likes} />
        <KeyValue label="Refreshed" value={refreshed} />
      </Card>
    </Screen>
  );
}

export const ButtonHooksScreen = defineScreen({
  component: ButtonHooks,
  options: (s) => s.hideBottomTabs(),
});
