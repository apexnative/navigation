import React from 'react';
import { defineScreen } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Card, KeyValue, Muted, Screen } from '../ui';

// Runs once, the first time a command shows the screen: the module loads then, not when the app starts.
const loadedAt = new Date().toLocaleTimeString();
log('state', 'Later: module loaded', loadedAt);

/** A screen whose module loads the first time a command shows it: `{ load: () => import(…) }` in `screens`. */
function Later({ from }: { from: string }) {
  return (
    <Screen>
      <Card title="Loaded later">
        <Muted>
          This module ran when the first push needed it. The push waited for it, so the title from
          its options was there from the first frame.
        </Muted>
        <KeyValue label="Module loaded at" value={loadedAt} />
        <KeyValue label="Opened from" value={from} />
      </Card>
    </Screen>
  );
}

export default defineScreen({
  component: Later,
  options: (s, props) => s.title(`Later · ${props.from}`).hideBottomTabs(),
});
