import React, { useState } from 'react';
import { defineScreen } from '@apexnative/navigation';
import { Card, KeyValue, Muted, Screen } from '../ui';

const ROWS = Array.from({ length: 12 }, (_, index) => `Row ${index + 1}`);

// Holds the JavaScript thread as a heavy first render does: nothing else runs until it ends.
function holdFor(ms: number): void {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    // A busy wait on purpose: the render takes this long.
  }
}

/**
 * A screen whose content takes `delay` ms to render the first time; its title comes with the push. How the push waits
 * for the content depends on `animations.push.waitForRender`.
 */
function Heavy({ delay = 0 }: { delay?: number }) {
  const [renderedIn] = useState(() => {
    const start = Date.now();
    holdFor(delay);
    return Date.now() - start;
  });
  return (
    <Screen>
      <Card title="The content">
        <Muted>
          Not loading: this screen stands for a heavy first render, the JavaScript thread busy
          building it (a big list built at once). All of this exists only once that render ended. A
          screen that loads data renders at once with a loader of its own and needs no wait.
        </Muted>
        <KeyValue label="First render" value={`${renderedIn} ms`} />
        {ROWS.map((row) => (
          <KeyValue key={row} label={row} value="drawn" />
        ))}
      </Card>
    </Screen>
  );
}

export const HeavyScreen = defineScreen({
  component: Heavy,
  options: (s) => s.hideBottomTabs(),
});
