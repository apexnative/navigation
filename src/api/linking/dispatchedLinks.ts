import { DeviceEventEmitter, Platform } from 'react-native';
import { onNativeEvent } from '../../engine/native';
import type { DeepLinkDispatchedEvent } from '../../interfaces/Events';

let listening = false;

/**
 * (iOS) Hands `Linking` the links the app dispatched that it didn't take from the native side: on React Native 0.88
 * and newer, in an app with a scene delegate, it takes only the links a scene opened. `Linking` listens to its `url`
 * events through `DeviceEventEmitter`, so every listener of the app gets them, the navigation's links too.
 */
export function listenToDispatchedLinks() {
  if (listening || Platform.OS !== 'ios') return;
  listening = true;
  onNativeEvent<DeepLinkDispatchedEvent>('DeepLinkDispatched', ({ url }) => {
    DeviceEventEmitter.emit('url', { url });
  });
}
