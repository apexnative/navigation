import React, { useState } from 'react';
import {
  defineScreen,
  useFocusEffect,
  useIsFocused,
  useNavigation,
  useNavigationEvent,
  useNavigationState,
  useScreenAppear,
  useScreenDisappear,
} from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

/**
 * What the hooks of a screen see, live: appear and disappear (also in the Journal), focus, a timer of
 * `useFocusEffect` that runs only while the screen shows, what the user sees, the last command.
 */
function HooksDemo() {
  const nav = useNavigation();
  const [appeared, setAppeared] = useState(0);
  const [disappeared, setDisappeared] = useState(0);
  const [seconds, setSeconds] = useState(0);
  const [lastAppeared, setLastAppeared] = useState<string>();
  const focused = useIsFocused();
  const shown = useNavigationState((state) => state.screen?.name ?? '—');

  useScreenAppear(() => {
    setAppeared((count) => count + 1);
    log('event', 'HooksDemo', 'useScreenAppear');
  });
  useScreenDisappear(() => {
    setDisappeared((count) => count + 1);
    log('event', 'HooksDemo', 'useScreenDisappear');
  });
  useFocusEffect(() => {
    log('event', 'HooksDemo', 'useFocusEffect: runs');
    const timer = setInterval(() => setSeconds((count) => count + 1), 1000);
    return () => {
      clearInterval(timer);
      log('event', 'HooksDemo', 'useFocusEffect: cleanup');
    };
  });
  useNavigationEvent('screenDidAppear', (event) => setLastAppeared(event.screen.name));

  return (
    <Screen>
      <Card title="What the hooks see">
        <KeyValue label="useScreenAppear" value={appeared} />
        <KeyValue label="useScreenDisappear" value={disappeared} />
        <KeyValue label="useIsFocused" value={focused ? 'yes' : 'no'} />
        <KeyValue label="useFocusEffect" value={`${seconds} s`} />
        <KeyValue label="useNavigationState" value={shown} />
        <KeyValue label="useNavigationEvent" value={lastAppeared ?? '—'} />
      </Card>
      <Card title="Cover it, then come back">
        <Muted>
          The timer stops while something covers the screen, a sheet too, and goes on when it shows
          again.
        </Muted>
        <Button title="Push Details" onPress={() => nav.push('Details')} />
        <Button title="A sheet" onPress={() => nav.showModal('Details', { closeButton: true })} />
        <Button
          title="A full screen modal"
          onPress={() => nav.showModal('Details', { style: 'fullScreen', closeButton: true })}
        />
      </Card>
    </Screen>
  );
}

export const HooksDemoScreen = defineScreen({
  component: HooksDemo,
  options: (s) => s.title('Hooks').hideBottomTabs(),
});
