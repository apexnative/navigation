import React, { useState } from 'react';
import { defineScreen } from '@apexnative/navigation';
import { Card, KeyValue, Muted, Screen } from '../ui';

const ROWS = Array.from({ length: 12 }, (_, index) => `Row ${index + 1}`);

type HeavyProps = { title: string; largeTitle?: boolean; delay?: number };

// Holds the JavaScript thread as a heavy first render does: nothing else runs until it ends.
function holdFor(ms: number): void {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    // A busy wait on purpose: the render takes this long.
  }
}

/**
 * A screen whose body takes `delay` ms to render the first time. Its title is static, in its options: only the body is
 * slow. How the push waits for the body depends on `animations.push.waitForRender`.
 */
function Heavy({ delay = 0 }: HeavyProps) {
  const [renderedIn] = useState(() => {
    const start = Date.now();
    holdFor(delay);
    return Date.now() - start;
  });
  return (
    <Screen>
      <Card title="The body">
        <Muted>
          This is not loading. The delay stands for a heavy first render: the JavaScript thread is
          busy building the screen (a big list built at once), and nothing of the body exists until
          it ends. A screen that loads data renders at once with its own loader and needs no wait.
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
  options: (s, props) =>
    s
      .title(props.title)
      .largeTitle(props.largeTitle ?? false)
      .hideBottomTabs(),
});
