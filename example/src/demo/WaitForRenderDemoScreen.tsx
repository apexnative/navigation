import React, { useState } from 'react';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import type { Options } from '@apexnative/navigation';
import { Button, Card, Label, Muted, Screen, Segmented } from '../ui';

type Wait = 'unset' | 'true' | 'false';

const WAITS: Wait[] = ['unset', 'true', 'false'];
const DELAYS = [300, 800, 1500];

// The push's options: its title, a large title or not, and `animations.push.waitForRender`, left out when unset.
function pushOptions(wait: Wait, title: string, largeTitle = false): Options {
  return {
    topBar: { title: { text: title }, ...(largeTitle && { largeTitle: { visible: true } }) },
    ...(wait !== 'unset' && { animations: { push: { waitForRender: wait === 'true' } } }),
  };
}

/** Pushes a screen whose first render is slow, plain or with a large title, with the `waitForRender` picked here. */
function WaitForRenderDemo() {
  const nav = useNavigation();
  const [wait, setWait] = useState<Wait>('unset');
  const [delay, setDelay] = useState(800);
  return (
    <Screen>
      <Card title="The push">
        <Label>animations.push.waitForRender</Label>
        <Segmented
          segments={WAITS.map((value) => ({
            key: value,
            label: value === 'unset' ? '—' : value,
            selected: wait === value,
            onPress: () => setWait(value),
          }))}
        />
        <Label>First render of the slow screen</Label>
        <Segmented
          segments={DELAYS.map((value) => ({
            key: String(value),
            label: `${value} ms`,
            selected: delay === value,
            onPress: () => setDelay(value),
          }))}
        />
        <Muted>
          The slow screen simulates a heavy first render, not loading: the JavaScript thread is busy
          building it for the time picked, as a big list built at once. true: the push starts once
          the content is drawn, and the screen slides in complete. —: the push waits half a second
          at most, then goes. false: it starts at once; the screen slides in empty and its content
          comes in later. A screen that loads data renders at once with its own loader and needs no
          wait.
        </Muted>
      </Card>
      <Card title="Push">
        <Button
          title="Push a slow screen"
          variant="primary"
          onPress={() =>
            nav.push('Heavy', { props: { delay }, options: pushOptions(wait, 'Slow screen') })
          }
        />
        <Button
          title="Push a plain screen"
          onPress={() =>
            nav.push('Heavy', { props: { delay: 0 }, options: pushOptions(wait, 'Plain screen') })
          }
        />
        <Button
          title="Push with a large title"
          onPress={() =>
            nav.push('Heavy', {
              props: { delay },
              options: pushOptions(wait, 'A large title', true),
            })
          }
        />
      </Card>
    </Screen>
  );
}

export const WaitForRenderDemoScreen = defineScreen({
  component: WaitForRenderDemo,
  options: (s) => s.title('Waiting to draw').hideBottomTabs(),
});
