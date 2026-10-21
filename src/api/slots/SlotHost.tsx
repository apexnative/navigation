import React, { memo, useEffect, useLayoutEffect, useMemo, useSyncExternalStore } from 'react';
import { StyleSheet } from 'react-native';
import {
  hostMountedChanged,
  type NativeScreenHook,
  nativeScreenHooks,
} from '../../engine/nativeScreens';
import { screenRoot } from '../../engine/screens';
import { type Slot, slotCommitted, slotStore } from '../../engine/slots';
import ANNSlot from './ANNSlotNativeComponent';

// The native side sizes a slot to its container and positions it absolutely; it starts at the corner of the host.
const styles = StyleSheet.create({ slot: { left: 0, top: 0 } });

// One per screen: a screen shown or gone does not render the others again.
const SlotScreen = memo(function SlotContent({ slot }: { slot: Slot }) {
  const Screen = screenRoot(slot.name);
  const { key, held, frame } = slot;
  const initialSize = useMemo(() => frame && [frame.width, frame.height], [frame]);
  // A screen laid out before its push is pushed once React committed it.
  useLayoutEffect(() => {
    if (frame && !held) slotCommitted(key);
  }, [key, held, frame]);
  if (!Screen || held) return null;
  return (
    <ANNSlot nativeID={slot.id} style={styles.slot} initialSize={initialSize}>
      <Screen componentId={slot.id} />
    </ANNSlot>
  );
});

// One per native screen with `useProps`: its hook, which renders nothing.
const NativeScreenProps = memo(function NativeScreenContent({ hook }: { hook: NativeScreenHook }) {
  const { Controller, componentId, props, onProps, onError } = hook;
  return <Controller componentId={componentId} props={props} onProps={onProps} onError={onError} />;
});

function NativeScreenHooks() {
  const hooks = useSyncExternalStore(nativeScreenHooks.subscribe, nativeScreenHooks.get);
  return hooks.map((hook) => <NativeScreenProps key={hook.key} hook={hook} />);
}

/**
 * The app's hidden React root, which the native side runs as `SLOT_HOST_MODULE`: in one tree, the app's provider
 * around a slot for every screen shown; in any mode, the hooks of the native screens (`useProps`), inside the app's
 * provider when there is one. Before the app starts, as after a reload, it renders no screens.
 */
export function SlotHost() {
  const { provider: Provider, slots } = useSyncExternalStore(slotStore.subscribe, slotStore.get);
  useEffect(() => {
    hostMountedChanged(true);
    return () => hostMountedChanged(false);
  }, []);
  // The app sets its provider as it launches, before a command shows a screen: the hooks never move under it.
  if (!Provider) return <NativeScreenHooks />;
  return (
    <Provider>
      {slots.map((slot) => (
        <SlotScreen key={slot.key} slot={slot} />
      ))}
      <NativeScreenHooks />
    </Provider>
  );
}
