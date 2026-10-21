import React, { use, useState } from 'react';
import { defineScreen, useScreen } from '@apexnative/navigation';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

// The data of each load, by screen and round: a load is one promise, however many times the screen renders.
const loads = new Map<string, Promise<string>>();

function load(key: string): Promise<string> {
  let promise = loads.get(key);
  if (!promise) {
    promise = new Promise((resolve) =>
      setTimeout(() => resolve(new Date().toLocaleTimeString()), 2000)
    );
    loads.set(key, promise);
  }
  return promise;
}

/**
 * A screen that waits for its data with React's `use`: while it waits, the screen shows `loadingFallback`. Load again
 * waits once more.
 */
function Slow() {
  const { id } = useScreen();
  const [round, setRound] = useState(1);
  const loadedAt = use(load(`${id}:${round}`));
  return (
    <Screen>
      <Card title="Loaded">
        <Muted>The bar was there at once; the content waited for its data.</Muted>
        <KeyValue label="Load" value={round} />
        <KeyValue label="Loaded at" value={loadedAt} />
        <Button title="Load again" onPress={() => setRound((count) => count + 1)} />
      </Card>
    </Screen>
  );
}

export const SlowScreen = defineScreen({
  component: Slow,
  options: (s) => s.title('Loading').hideBottomTabs(),
});
