import React, { useState } from 'react';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import type { Options } from '@apexnative/navigation';
import { Button, Card, Label, Muted, Screen, Segmented } from '../ui';

type Wait = 'unset' | 'true' | 'false';

const WAITS: Wait[] = ['unset', 'true', 'false'];
const DELAYS = [300, 800, 1500];

// The push's options: only `animations.push.waitForRender`, left out when unset. The title is the screen's own.
function pushOptions(wait: Wait): Options {
  return wait === 'unset' ? {} : { animations: { push: { waitForRender: wait === 'true' } } };
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
          Not loading: the delay stands for a heavy first render, the JavaScript thread busy
          building the screen's body. The title is static, in the screen's options, and comes with
          the push. true: the push starts once the body is drawn, the screen slides in complete. —:
          the push waits half a second at most: 300 ms slides in complete, 800 and 1500 ms slide in
          without the body, which comes in later. false: the push starts at once, the body always
          comes in later. A screen that loads data renders at once with its own loader.
        </Muted>
      </Card>
      <Card title="Push">
        <Button
          title="Push a slow screen"
          variant="primary"
          onPress={() =>
            nav.push('Heavy', {
              props: { title: 'Slow screen', delay },
              options: pushOptions(wait),
            })
          }
        />
        <Button
          title="Push a plain screen"
          onPress={() =>
            nav.push('Heavy', {
              props: { title: 'Plain screen', delay: 0 },
              options: pushOptions(wait),
            })
          }
        />
        <Button
          title="Push with a large title"
          onPress={() =>
            nav.push('Heavy', {
              props: { title: 'A large title', largeTitle: true, delay },
              options: pushOptions(wait),
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
