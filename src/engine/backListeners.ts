import { BackHandler, Platform } from 'react-native';
import { commands } from './native';

// The back handlers of `BackHandler`, kept as React Native keeps them: one entry per function, whatever the event name.
const handlers = new Set<unknown>();
let watching = false;

/**
 * Tells the native side whether JavaScript listens to the back button (Android). The navigation takes the back button
 * only while it has something to go back to or JavaScript listens: on the first screen the system then plays its own
 * animation back to the home screen. React Native keeps the listeners to itself, so `BackHandler.addEventListener` is
 * wrapped to count them; a listener added before `createNavigation` was called isn't counted.
 */
export function watchBackListeners(): void {
  if (watching || Platform.OS !== 'android') return;
  watching = true;
  const add = BackHandler.addEventListener;
  const report = () => commands?.setBackListeners?.(handlers.size > 0);
  Object.assign(BackHandler, {
    addEventListener(...args: Parameters<typeof add>): ReturnType<typeof add> {
      const [, handler] = args;
      const subscription = add.apply(BackHandler, args);
      if (!handlers.has(handler)) {
        handlers.add(handler);
        if (handlers.size === 1) report();
      }
      return {
        ...subscription,
        remove() {
          subscription.remove();
          if (handlers.delete(handler) && handlers.size === 0) report();
        },
      };
    },
  });
}
